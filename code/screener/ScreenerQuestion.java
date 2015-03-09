package screener;

import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.PropertiesClientConfig;
import com.amazonaws.mturk.addon.HITQuestion;
import com.amazonaws.mturk.requester.*;
import java.io.*;
import java.util.Scanner;

public class ScreenerQuestion {
  private RequesterService service;

  // Defining the attributes of the HIT to be created
  private String title = "YOURTITLEHERE";
  private String description = "YOUR DESCRIPTION HERE";
    private String keywords = "survey";
  private int numAssignments = 20;
  private double reward = 0.50;
  private long autoApprovalDelay = 60*60*48; // 48 hours
  private long assignmentDuration = 60*60; // 60 minutes
  private long lifetime = 60*60*48; // 48 hours
  private String annotation = "screener";

  private boolean invisible = false;
  private boolean haveQualifications = false;
  private boolean append = true;

  public ScreenerQuestion() {
    service = new RequesterService(new PropertiesClientConfig("../mturk.properties"));
  }

  public boolean hasEnoughFund() {
    double balance = service.getAccountBalance();
    System.out.println("Got account balance: " + RequesterService.formatCurrency(balance));
    return balance > reward;
  }

  public void createScreener() {
    try {

      QualificationRequirement[] requirements = null;
      if(haveQualifications) {
        requirements = new QualificationRequirement[3];
        requirements[0] = new QualificationRequirement(RequesterService.APPROVAL_RATE_QUALIFICATION_TYPE_ID, Comparator.GreaterThanOrEqualTo, 95, null, invisible); //>=95% approval
        requirements[1] = new QualificationRequirement("00000000000000000040", Comparator.GreaterThanOrEqualTo, 1000, null, invisible); //>=1000 HITs
        requirements[2] = new QualificationRequirement(RequesterService.LOCALE_QUALIFICATION_TYPE_ID, Comparator.EqualTo, null, new Locale("US"), invisible); //USA! USA!
      }

      String[] responseGroup = new String[]{"Minimal"};

      HITQuestion question = new HITQuestion("screenerquestion.xml");

      String hitType = service.registerHITType(autoApprovalDelay, assignmentDuration, reward, title, keywords, description, requirements);

      HIT hit = service.createHIT(hitType, title, description, keywords, question.getQuestion(), reward, assignmentDuration, autoApprovalDelay, lifetime, numAssignments, annotation, requirements, responseGroup);

      System.out.println("Created HIT: " + hit.getHITId());

      System.out.println("You may see your HIT with HITTypeId '" 
          + hit.getHITTypeId() + "' here: ");
      System.out.println(service.getWebsiteURL() + "/mturk/preview?groupId=" + hit.getHITTypeId());

      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("hitid.txt", append))); //Should hopefully append the hit id.
      writer.println(hit.getHITId() + " " + service.getWebsiteURL() + "/mturk/preview?groupId=" + hit.getHITTypeId());
      writer.close();

    } catch (Exception e) {
      System.err.println(e.getLocalizedMessage());
    }
  }

  public static void main(String[] args) {
    ScreenerQuestion app = new ScreenerQuestion();

    if (app.hasEnoughFund()) {
      app.createScreener();
      System.out.println("Success.");
    } else {
      System.out.println("You do not have enough funds to create the HIT.");
    }
  }
}
