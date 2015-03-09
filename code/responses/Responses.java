package responses;

import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.PropertiesClientConfig;
import com.amazonaws.mturk.addon.HITQuestion;
import com.amazonaws.mturk.requester.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import com.csvreader.CsvReader;

public class Responses {
  private Scanner in = new Scanner(System.in);
  private RequesterService service;
  private boolean append = true;

  public Responses() {
    service = new RequesterService(new PropertiesClientConfig("../mturk.properties"));
  }

  public HashMap<String, Assignment> getTurkResponses() {
    try {
      HashMap<String, Assignment> map = new HashMap<String, Assignment>();
      Scanner scanner = new Scanner(new File("../full/hitid.txt"));
      while(scanner.hasNext()) {
        String hitId = scanner.next();
        String hitTypeIdURL = scanner.next();
        Assignment[] assignments = service.getAllAssignmentsForHIT(hitId);
        for(Assignment a : assignments) {
          if(a.getAssignmentStatus() == AssignmentStatus.Submitted) {
            String freeResponse = a.getAnswer().split("\\<FreeText\\>")[1].split("\\<\\/FreeText\\>")[0].trim(); //This line is a massive hack, but fuck java and doublefuck xml.
            map.put(freeResponse, a);
          }
        }
      }
      System.out.println("There are " + map.size() + " assignments on MTurk.");
      scanner.close();
      return map;
    } catch(Exception e){
      System.err.println(e.getLocalizedMessage());
      return null;
    }
  }

  public void crossCheckWithSurveyGizmo(HashMap<String, Assignment> turkResponses) {
    try {
      PrintWriter responses = new PrintWriter(new BufferedWriter(new FileWriter("responses.csv", append)));
      HashMap<String, CsvReader> csvs = new HashMap<String, CsvReader>();
      CsvReader csv = new CsvReader("../full/gizmo.csv");
      csv.readHeaders();
      while(csv.readRecord()) {
        String session = csv.get("SessionID").trim();
        if(turkResponses.containsKey(session)) {
          System.out.println("Session ID: " + session);
          responses.println(session + "," + turkResponses.get(session).getWorkerId());
          //We found a match! Accept the HIT.
          manageResponse(csv, turkResponses.get(session));
          turkResponses.remove(session);
        } else {
          //There's a screener response that doesn't show up on MTurk.
          //I guess that's fine or something.
        }
      }
      
      responses.close();
      for(String key : turkResponses.keySet()) {
        System.out.println("Look up record for Turker " + turkResponses.get(key).getWorkerId() + ", " + key + ". Delay Judgment? [Y/N]");
        boolean accept = in.next().equals("Y");
        if(!accept) {
          System.out.println("Accept? [Y/N]");
          accept = in.next().equals("Y");
          if(!accept) {
            System.out.println("Rejecting HIT from Turker " + turkResponses.get(key).getWorkerId());
            service.rejectAssignment(turkResponses.get(key).getAssignmentId(), "Your confirmation code did not match any in our records.");    
          }
          else {
            System.out.println("Enter the matching session id");
            String session = in.next().trim();
            manageResponse(csvs.get(key), turkResponses.get(session));
          }
        } else {
          //Delaying judgment is a no-op. It'll come up again the next time, presumably.
        }
      }
    } catch(Exception e) {
      System.err.println(e.getLocalizedMessage());
    }
  }

  public void manageResponse(CsvReader csv, Assignment assignment) {
    try {
      System.out.println("Reviewing response from " + assignment.getWorkerId());
      //Check response quality, if it's bad reject the HIT
      boolean accept = getFreeResponseResult(csv.get("free_response_question"));
      if(accept) {
        service.approveAssignment(assignment.getAssignmentId(), "Thank you for your response!");
      } else {
        service.rejectAssignment(assignment.getAssignmentId(), "Your response does not match your response to the screener questions.");
      }
    } catch(Exception e) {
      System.err.println(e.getLocalizedMessage());
    }
  }

  private boolean getFreeResponseResult(String response) {
    System.out.println(response);
    String accept = "";
      while(!accept.equals("N") && !accept.equals("Y")) {
        System.out.println("Accept? Y/N");
        accept = in.next();
      }
    return accept.trim().equals("Y");
  }

  public static void main(String[] args) {

    Responses app = new Responses();

    HashMap<String, Assignment> turkResponses = app.getTurkResponses();
    app.crossCheckWithSurveyGizmo(turkResponses);
  }
}
