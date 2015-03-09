package full;

import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.PropertiesClientConfig;
import com.amazonaws.mturk.addon.HITQuestion;
import com.amazonaws.mturk.requester.*;
import java.io.*;
import java.util.Scanner;
import java.util.HashMap;

public class FullSurvey {
  private Scanner in = new Scanner(System.in);

  private RequesterService service;

  //TODO: Config stuff. Set later.
  private boolean invisible = false;
  private boolean haveQualifications = true;
  private boolean append = true;
  private static final int NUMASSIGNMENTS = 10;
  private static final double PAYMENT = 2.00;
  private static final int AUTOAPPROVALDELAY = 60*60*48;
  private static final int DURATION = 60*60*2;
  private static final int LIFETIME = 60*60*48;
  private static final String HITTITLE = "YOUR TITLE HERE";
  private static final String HITDESCRIPTION = "YOUR DESCRIPTION HERE";
  private static final String HITKEYWORDS = "HIT KEYWORDS";
  private static final String ANNOTATION = "ANNOTATION";
  private SurveyParameters parameters = new SurveyParameters(HITTITLE, HITDESCRIPTION, HITKEYWORDS, ANNOTATION, NUMASSIGNMENTS, PAYMENT, AUTOAPPROVALDELAY, DURATION, LIFETIME);
  //These should be duplicated from the QualifyScreenerRespondents.java file.
  private static final String qualification_name = "YOURQUALIFICATIONNAMEHERE";
  private static final String qualification_theme = "YOUR THEME HERE";
  private static final String qualification_description = "YOUR DESCRIPTION HERE";

  public FullSurvey() {
    service = new RequesterService(new PropertiesClientConfig("../mturk.properties"));
  }

  public boolean hasEnoughFund(double reward) {
    double balance = service.getAccountBalance();
    System.out.println("Got account balance: " + RequesterService.formatCurrency(balance));
    return balance > reward;
  }

  public String getQualificationTypeId() {
    try {
      File qualificationFile = new File("../screener/qualification_id.txt");
      if(qualificationFile.exists()) {
        Scanner scanner = new Scanner(qualificationFile);
        String qualificationTypeId = scanner.next();
        scanner.close();
        return qualificationTypeId;
      } else {
        QualificationType qualification = service.createQualificationType(qualification_name, qualification_theme, qualification_description, QualificationTypeStatus.Active, (long)1, null, null, null, false, null);
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("../screener/qualification_id.txt", false)));
        writer.println(qualification.getQualificationTypeId());
        writer.close();
        return qualification.getQualificationTypeId();
      }
    } catch(Exception e) {
      System.err.println(e.getLocalizedMessage());
      return "";
    }
  }

  public void createSurvey() {
    try {

      QualificationRequirement[] requirements = null;
      if(haveQualifications) {
        requirements = new QualificationRequirement[4];
        requirements[0] = new QualificationRequirement(RequesterService.LOCALE_QUALIFICATION_TYPE_ID, Comparator.EqualTo, null, new Locale("US"), invisible); //USA! USA!
        requirements[1] = new QualificationRequirement(getQualificationTypeId(), Comparator.EqualTo, 1, null, invisible); //Completed the screener
        requirements[2] = new QualificationRequirement(RequesterService.APPROVAL_RATE_QUALIFICATION_TYPE_ID, Comparator.GreaterThanOrEqualTo, 95, null, invisible); //>=95% approval
        requirements[3] = new QualificationRequirement("00000000000000000040", Comparator.GreaterThanOrEqualTo, 1000, null, invisible); //>=1000 HITs
      }

      String[] responseGroup = new String[]{"Minimal"};

      HITQuestion question = new HITQuestion("fullsurveyquestion.xml");

      SurveyParameters p = parameters;

      String hitType = service.registerHITType(p.autoApprovalDelay, p.assignmentDuration, p.reward, p.title, p.keywords, p.description, requirements);

      HIT hit = service.createHIT(hitType, p.title, p.description, p.keywords, question.getQuestion(), p.reward, p.assignmentDuration, p.autoApprovalDelay, p.lifetime, p.numAssignments, p.annotation, requirements, responseGroup);

      System.out.println("Created HIT: " + hit.getHITId());

      System.out.println("You may see your HIT with HITTypeId '" 
          + hit.getHITTypeId() + "' here: ");
      System.out.println(service.getWebsiteURL() 
          + "/mturk/preview?groupId=" + hit.getHITTypeId());

      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("hitid.txt", append))); //Should hopefully append the hit id.
      writer.println(hit.getHITId() + " " + service.getWebsiteURL() + "/mturk/preview?groupId=" + hit.getHITTypeId());
      writer.close();

    } catch (Exception e) {
      System.err.println(e.getLocalizedMessage());
    }
  }

  public static void main(String[] args) {
    FullSurvey app = new FullSurvey();

    if(app.hasEnoughFund(app.parameters.reward)) {
      app.createSurvey();
    } else {
      System.out.println("You do not have enough funds to create the HIT.");
    }
    System.out.println("Success.");
  }
}

class SurveyParameters {
  public String title;
  public String description;
  public String keywords;
  public String annotation;
  public int numAssignments;
  public double reward;
  public long autoApprovalDelay;
  public long assignmentDuration;
  public long lifetime;

  public SurveyParameters(String title, String description, String keywords, String annotation, int numAssignments, double reward, long autoApprovalDelay, long assignmentDuration, long lifetime) {
    this.title = title;
    this.description = description;
    this.keywords = keywords;
    this.annotation = annotation;
    this.numAssignments = numAssignments;
    this.reward = reward;
    this.autoApprovalDelay = autoApprovalDelay;
    this.assignmentDuration = assignmentDuration;
    this.lifetime = lifetime;
  }
}