package qualifier;

import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.PropertiesClientConfig;
import com.amazonaws.mturk.addon.HITQuestion;
import com.amazonaws.mturk.requester.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import com.csvreader.CsvReader;

public class QualifyScreenerRespondents {
  private Scanner in = new Scanner(System.in);
  private RequesterService service;
  private static final String qualification_name = "YOURQUALIFICATIONNAMEHERE";
  private static final String qualification_theme = "YOUR THEME HERE";
  private static final String qualification_description = "YOUR DESCRIPTION HERE";

  public QualifyScreenerRespondents() {
    service = new RequesterService(new PropertiesClientConfig("../mturk.properties"));
  }

  public HashMap<String, Assignment> getTurkResponses() {
    try {
      HashMap<String, Assignment> map = new HashMap<String, Assignment>();
      Scanner scanner = new Scanner(new File("../screener/hitid.txt"));
      while(scanner.hasNext()) {
        String hitId = scanner.next();
        String hitTypeURL = scanner.next();
        Assignment[] assignments = service.getAllAssignmentsForHIT(hitId);
        for(Assignment a : assignments) {
          if(a.getAssignmentStatus() == AssignmentStatus.Submitted) {
            String freeResponse = a.getAnswer().split("\\<FreeText\\>")[1].split("\\<\\/FreeText\\>")[0].trim();
            //Apparently there's a more methodical way of doing this, but I'm lazy and this worked.
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

  public String getQualificationTypeId() {
    try {
      File qualificationFile = new File("qualification_id.txt");
      if(qualificationFile.exists()) {
        Scanner scanner = new Scanner(qualificationFile);
        String qualificationTypeId = scanner.next();
        scanner.close();
        return qualificationTypeId;
      } else {
        QualificationType qualification = service.createQualificationType(qualification_name, qualification_theme, qualification_description, QualificationTypeStatus.Active, (long)1, null, null, null, false, null);
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("qualification_id.txt", false)));
        writer.println(qualification.getQualificationTypeId());
        writer.close();
        return qualification.getQualificationTypeId();
      }
    } catch(Exception e) {
      System.err.println(e.getLocalizedMessage());
      return "";
    }
  }

  public boolean fitInclusionCriteria(CsvReader csv) {
    try {
      return csv.get("screener_question").equals("Yes"); //Replace with your own inclusion criteria
    } catch(Exception e) {
      System.err.println(e.getLocalizedMessage());
      return false;
    }
  }

  public void crossCheckWithSurveyGizmo(HashMap<String, Assignment> turkResponses) {
    try {
      CsvReader csv = new CsvReader("../screener/gizmo.csv");
      csv.readHeaders();
      while(csv.readRecord()) {
        String session = csv.get("SessionID").trim();
        if(turkResponses.containsKey(session)) {
          System.out.println("Session ID: " + session);
          //We found a match! Accept the HIT.
          manageResponse(csv, turkResponses.get(session));
          turkResponses.remove(session);
        } else {
          //There's a screener response that doesn't show up on MTurk.
          //I guess that's fine or something.
        }
      }
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
            manageResponse(csv, turkResponses.get(session));
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
      boolean fitCriteria = fitInclusionCriteria(csv);
      if(fitCriteria) { //Fit inclusion criteria
        System.out.println(assignment.getWorkerId() + " fits inclusion criteria.");
        boolean accept = getFreeResponseResult(csv.get("free_response_question"));
        if(accept) {
          try{
            //Grant additional qualification that will let them do the full survey.
              service.assignQualification(getQualificationTypeId(), assignment.getWorkerId(), 1, true);
          } catch(Exception e) {
            //They probably already have the qualification.
            System.err.println(e.getLocalizedMessage());
          }

          service.approveAssignment(assignment.getAssignmentId(), "Thank you for your response! We have another survey we are interested in hearing your response to.");
          //Message them(?) letting them know that there's a new HIT with a full survey.
        } else {
          service.rejectAssignment(assignment.getAssignmentId(), "Sorry, the free response question was not adequately answered.");
        }
      } else {
        //If the quality is good but the turker doesn't fit the inclusion criteria, accept hit and continue
        System.out.println(assignment.getWorkerId() + " does not fit inclusion criteria.");
        boolean accept = getFreeResponseResult(csv.get("free_response_question"));
        if(accept) {
          service.approveAssignment(assignment.getAssignmentId(), "Thank you for your response!");
        } else {
          service.rejectAssignment(assignment.getAssignmentId(), "Sorry, the free response question was not adequately answered.");
        }
      }
    } catch(Exception e) {
      System.err.println(e.getLocalizedMessage());
    }
  }

  private boolean getFreeResponseResult(String response) {
    System.out.println(response);
    return acceptLogic();
  }

  private boolean getFreeResponseResult(String[] responses) {
    for(String s : responses)
      System.out.println(s);
    return acceptLogic();
  }

  private boolean acceptLogic() {
    String accept = "";
      while(!accept.equals("N") && !accept.equals("Y")) {
        System.out.println("Accept? Y/N");
        accept = in.next();
      }
    return accept.trim().equals("Y");
  }

  public static void main(String[] args) {

    QualifyScreenerRespondents app = new QualifyScreenerRespondents();

    HashMap<String, Assignment> turkResponses = app.getTurkResponses();
    app.crossCheckWithSurveyGizmo(turkResponses);
  }
}
