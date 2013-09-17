package ca.ubc.clicker.server.filters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ca.ubc.clicker.driver.exception.ClickerException;
import ca.ubc.clicker.server.ClickerServer;
import ca.ubc.clicker.server.gson.GsonFactory;
import ca.ubc.clicker.server.messages.ChoiceMessage;
import ca.ubc.clicker.server.messages.CommandResponseMessage;
import ca.ubc.clicker.server.messages.ResponseMessage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MultipleInstructorFilter implements Filter {
	private static Logger log = LogManager.getLogger();
	private static final String CHOICES = "{\"type\":\"choices\"";
	private static final String STATUS = "\"command\":\"status\"";
	private static final String ENABLE_CHOICES = "{\"command\":\"enable choices\"}";
	private static final String DISABLE_CHOICES = "{\"command\":\"disable choices\"}";
	public static final String COMMAND_ENABLE_CHOICES = "enable choices";
	public static final String COMMAND_DISABLE_CHOICES = "disable choices";	
	
	private JsonParser parser;
    private List<String> instructorIds;
    private boolean acceptingChoices;
    private ClickerServer server; 
    
    public MultipleInstructorFilter() {
    	parser = new JsonParser();
    	instructorIds = new LinkedList<String>();
    	// TODO: configure instructor IDs
    	instructorIds.add("Peter");
    	instructorIds.add("371BA68A");
    	
    	StringBuffer instructors = new StringBuffer();
    	int numInstructors = instructorIds.size();
    	for (int i = 0; i < numInstructors; i++) {
    		instructors.append(instructorIds.get(i));
    		if ((i + 1) != numInstructors) {
    			instructors.append(", ");
    		}
    	}
    	if (numInstructors > 0) {
    		log.info("Initialized with extra instructors {}.", instructors.toString());
    	} else {
    		log.info("Initialized with no extra instructors.");
    	}
    	acceptingChoices = false;
    }
    
    public String toString() {
    	return "Multiple Instructors Filter";
    }
    
    @Override
    public void initialize(ClickerServer server) {
    	this.server = server;
    	instructorIds.add(server.getInstructorId());
    	try {
    		// turn on the hardware for accepting votes
			server.startAcceptingVotes();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClickerException e) {
			e.printStackTrace();
		}
    }
    
    private Gson gson() {
		return GsonFactory.gson();
	}
	
	@Override
	public String output(String message) {
		// intercept choices
		if (message.indexOf(CHOICES) == 0) {
			try {
				JsonObject jsonObj = parser.parse(message).getAsJsonObject();
				JsonArray data = jsonObj.get("data").getAsJsonArray();
				
				// add instructor tags and optionally discard all clicks that 
				// aren't instructors if voting isn't open
				ChoiceMessage[] choices = filterInstructors(data);
				
				if (choices.length == 0) {
					return null;
				}
				
				// re-serialize
				ResponseMessage response = new ResponseMessage();
				response.type = "choices";
				response.data = choices;
				
				return gson().toJson(response);
				
			} catch (IllegalStateException e) { }
		} 
		
		// intercept status and override the acceptingChoices property
		else if (message.indexOf(STATUS) != -1) {
			message = message.replaceAll("\"acceptingChoices\":(true|false)", "\"acceptingChoices\":" + acceptingChoices);
		}
		
		return message;
	}
	
	private ChoiceMessage[] filterInstructors(JsonArray data) {
		ArrayList<ChoiceMessage> choiceList = new ArrayList<ChoiceMessage>(data.size());
		
		Gson gson = gson();

		for (int i = 0; i < data.size(); i++) {
			// convert to object and change attribute value
			ChoiceMessage choice = gson.fromJson(data.get(i), ChoiceMessage.class);
			if (isInstructor(choice)) {
				choice.instructor = true;
				choiceList.add(choice);
			} else if (acceptingChoices) { // only add other choices if accepting votes
				choiceList.add(choice);
			}
		}
		
		return choiceList.toArray(new ChoiceMessage[choiceList.size()]);
	}
	
	private boolean isInstructor(ChoiceMessage choice) {
		return instructorIds.contains(choice.id);
	}

	@Override
	public String input(String message) {
		if (message == null) {
			return null;
		}
		
		// intercept enable choices and disable choices
		if (ENABLE_CHOICES.equals(message.trim())) {
			acceptingChoices = true;
			message = null; // set message to null to prevent further filtering. 

			log.info("[filter] {}", COMMAND_ENABLE_CHOICES);
			
			CommandResponseMessage response = new CommandResponseMessage();
			response.command = COMMAND_ENABLE_CHOICES;
			response.data = true;
			server.output(gson().toJson(response));
		} else if(DISABLE_CHOICES.equals(message.trim())) {
			acceptingChoices = false;
			message = null;
			
			log.info("[filter] {}", COMMAND_DISABLE_CHOICES);
			
			CommandResponseMessage response = new CommandResponseMessage();
			response.command = COMMAND_DISABLE_CHOICES;
			response.data = true;
			server.output(gson().toJson(response));
		}
		return message;
	}
}
