package com.uci.strokestudy.ucistrokestudy2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import cz.msebera.android.httpclient.Header;

public class MainActivity extends Activity {
    static final int[] totalSurveyQuestions = {7, 11, 9};
    SQLiteDatabase assessmentDatabase;
    private String siteNo = "1";
    private String userID;
    private String[] answers = new String[3];
    private int[] score = new int[3];
    private int currentQ = 0;
    private int session = 0;
    private int currentSurvey = 0;
    private View postDataButton;
    private String[][] survey = new String[3][33];

    /**
     * Override of Android onBackPressed
     * <p/>
     * Set so the back button does not close the assessment by accident.
     */
    @Override
    public void onBackPressed() {
    }

    /**
     * Override of Android onCreate
     * <p/>
     * Sets all local data (site number and local assessment), as well as populating the survey and
     * creating the local assessment database.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //SharedPreference used to hold the siteNo, if file not found just set siteNo to 1
        SharedPreferences prefFile = this.getPreferences(Context.MODE_PRIVATE);
        siteNo = prefFile.getString("siteNo", "1");

        populateSurvey();

        //The local SQLite Database used to store assessments that failed to get sent to server
        //If the database does not exist yet (first run) it will create one
        assessmentDatabase = openOrCreateDatabase("assessments", MODE_PRIVATE, null);
        assessmentDatabase.execSQL("CREATE TABLE IF NOT EXISTS assessments(id INTEGER PRIMARY KEY,siteNo VARCHAR,visit VARCHAR,answers VARCHAR,score VARCHAR,userID VARCHAR, url VARCHAR);");

        setContentView(R.layout.activity_main);
        checkLocal();
        ((Button) findViewById(R.id.siteLabel)).setText("Site " + siteNo);
    }

    /**
     * Override of Android onResume()
     * <p/>
     * Set so whenever the app is resumed it notifies the user if there is network available.
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (!isNetworkAvailable()) {
            Toast.makeText(MainActivity.this, "Network Unavailable", Toast.LENGTH_LONG).show();
        }

    }

    /**
     * loadUser
     * <p/>
     * Resets all user assessment variables and shows the start button if the userID is valid
     *
     * @param view The button view used to load the user
     */
    public void loadUser(View view) {
        //Reset all variables
        answers[0] = "";
        answers[1] = "";
        answers[2] = "";
        score[0] = 0;
        score[1] = 0;
        score[2] = 0;
        currentQ = 0;
        currentSurvey = 0;
        session = 0;

        //Gets the UserID text and sets it to userID
        EditText userID_EditText = (EditText) findViewById(R.id.userID);
        userID = userID_EditText.getText().toString();

        if (!userID.equals("")) {
            //The userID is valid, hide the userID textbox and button and display the start button
            userID_EditText.setVisibility(View.GONE);
            view.setVisibility(View.GONE);

            findViewById(R.id.mainLayout).setBackgroundResource(R.drawable.backgroundpaces);
            findViewById(R.id.sessionLayout).setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(MainActivity.this, "Please enter a user id", Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * setSession
     * <p/>
     * Sets the session variable based on the button pressed.
     *
     * @param view The session button (Baseline, Week1, Week6)
     */
    public void setSession(View view) {
        switch (view.getId()) {
            case R.id.baseline:
                session = 0;
                break;
            case R.id.week1:
                session = 1;
                break;
            case R.id.week6:
                session = 2;
                break;
            default:
                break;
        }

        findViewById(R.id.sessionLayout).setVisibility(View.GONE);
        findViewById(R.id.start2).setVisibility(View.VISIBLE);
    }

    /**
     * setSite
     * <p/>
     * Opens an AlertDialog for the user to set a new siteNo, called from site number button.
     *
     * @param view Site number button
     */
    public void setSite(View view) {
        final EditText txtNo = new EditText(this);
        final Button label = (Button) view;
        txtNo.setHint(siteNo);
        txtNo.setInputType(InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle("Site #")
                .setView(txtNo)
                .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        siteNo = txtNo.getText().toString();
                        label.setText("Site " + siteNo);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();

    }

    /**
     * startSurvey
     * <p/>
     * Saves the siteNo to the SharedPreferences, switches layout to survey, and loads the next question.
     *
     * @param view
     */
    public void startSurvey(View view) {
        SharedPreferences prefFile = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefFile.edit();
        editor.putString("siteNo", siteNo);
        editor.apply();

        setContentView(R.layout.layout_survey);
        loadQuestion();
    }

    /**
     * nextSurvey
     * <p/>
     * Saves the current survey results. The next survey layout is actually loaded in saveSurvey function.
     *
     * @param view
     */
    public void nextSurvey(View view) {
        view.setVisibility(View.GONE);
        postDataButton = view;
        switch (currentSurvey) {
            case 0:
                saveSurvey(0, "http://www1.icts.uci.edu/telerehab/ed/updatepaces.aspx", session, false);
                break;
            case 1:
                saveSurvey(1, "http://www1.icts.uci.edu/telerehab/ed/updateops.aspx", session, false);
                break;
            default:
                break;
        }

    }

    /**
     * submitAnswer
     * <p/>
     * Called from an Answer button, updates the answer array and score as well as
     * loads the next step (Next Question, Complete)
     *
     * @param view The answer button
     */
    public void submitAnswer(View view) {
        activateButtons(false);
        int ansVal;
        switch (view.getId()) {
            case R.id.survey_answer1:
                ansVal = 1;
                break;
            case R.id.survey_answer7:
                ansVal = 7;
                break;
            default:
                ansVal = Integer.parseInt(((Button) view).getText().toString());
                break;
        }

        score[currentSurvey] += ansVal;
        answers[currentSurvey] += Integer.toString(ansVal);

        ++currentQ;
        if (currentQ <= totalSurveyQuestions[currentSurvey]) {
            answers[currentSurvey] += ",";
            loadQuestion();
            activateButtons(true);
        } else {
            if ((session == 0 && currentSurvey == 1) || currentSurvey == 2) {
                setContentView(R.layout.layout_complete);
            } else {
                setContentView(R.layout.layout_nextsection);
            }
        }
    }

    /**
     * activateButtons
     * <p/>
     * Activates or Deactivates answer buttons. Used to prevent users from accidentally clicking on
     * an answer twice and skipping a question.
     *
     * @param activate True to enable, False to disable
     */
    private void activateButtons(boolean activate) {
        if (activate) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    findViewById(R.id.survey_answer1).setEnabled(true);
                    findViewById(R.id.survey_answer2).setEnabled(true);
                    findViewById(R.id.survey_answer3).setEnabled(true);
                    findViewById(R.id.survey_answer4).setEnabled(true);
                    findViewById(R.id.survey_answer5).setEnabled(true);
                    findViewById(R.id.survey_answer6).setEnabled(true);
                    findViewById(R.id.survey_answer7).setEnabled(true);
                }
            }, 250);
        } else {
            findViewById(R.id.survey_answer1).setEnabled(false);
            findViewById(R.id.survey_answer2).setEnabled(false);
            findViewById(R.id.survey_answer3).setEnabled(false);
            findViewById(R.id.survey_answer4).setEnabled(false);
            findViewById(R.id.survey_answer5).setEnabled(false);
            findViewById(R.id.survey_answer6).setEnabled(false);
            findViewById(R.id.survey_answer7).setEnabled(false);
        }
    }

    /**
     * save
     * <p/>
     * Saves the current survey (save is called from the button which then calls saveSurvey).
     * save is only used to decide which url the survey is going to be posted to and then calls
     * saveSurvey with that url.
     *
     * @param view
     */
    public void save(View view) {
        postDataButton = view;
        view.setVisibility(View.GONE);
        if (currentSurvey == 1) {
            saveSurvey(1, "http://www1.icts.uci.edu/telerehab/ed/updateops.aspx", session, true);
        } else {
            saveSurvey(2, "http://www1.icts.uci.edu/telerehab/ed/updatesat.aspx", session - 1, true);
        }
    }

    /**
     * saveSurvey
     * <p/>
     * Posts the survey data to the UCI webpage.
     *
     * @param surveyNo The surveyNo being sent (PACES,OPS,SAT)
     * @param url      The url of the post webpage
     * @param session  The session of the user (Baseline, Week1, Week6)
     * @param last     True if last survey, else False
     */
    private void saveSurvey(final int surveyNo, final String url, final int session, final boolean last) {

        RequestParams param = new RequestParams();
        param.put("txtVisit", Integer.toString(session + 1));
        param.put("txtSite", siteNo);
        param.put("txtAnswers", answers[surveyNo]);
        param.put("txtScore", Integer.toString(score[surveyNo]));
        param.put("userID", userID);

        Log.d("PARAMS[" + Integer.toString(surveyNo) + "]: ", param.toString());

        AsyncHttpClient client = new AsyncHttpClient();
        client.setEnableRedirects(true);
        client.post(url, param, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK
                if (last) {
                    setContentView(R.layout.activity_main);
                    checkLocal();
                    ((Button) findViewById(R.id.siteLabel)).setText("Site " + siteNo);
                } else {
                    ++currentSurvey;
                    currentQ = 0;
                    startSurvey(MainActivity.this.postDataButton);
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                //((Button) MainActivity.this.postDataButton).setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Saved Locally", Toast.LENGTH_SHORT).show();
                assessmentDatabase.execSQL("INSERT INTO assessments VALUES(null,'" + siteNo + "','" + Integer.toString(session + 1) + "','" + answers[surveyNo] + "','" + Integer.toString(score[surveyNo]) + "','" + userID + "','" + url + "');");

                if (last) {
                    setContentView(R.layout.activity_main);
                    checkLocal();
                    ((Button) findViewById(R.id.siteLabel)).setText("Site " + siteNo);
                } else {
                    ++currentSurvey;
                    currentQ = 0;
                    startSurvey(MainActivity.this.postDataButton);
                }
            }
        });
    }

    /**
     * checkLocal
     * <p/>
     * Checks to see if there are any local assessments, if there are none hide the saveLocal button
     */
    private void checkLocal() {
        Cursor resultSet = assessmentDatabase.rawQuery("Select * from assessments", null);
        if (resultSet.getCount() == 0) {
            findViewById(R.id.saveLocal).setVisibility(View.GONE);
        }
    }

    /**
     * saveLocal
     * <p/>
     * Called from the saveLocal button, uses saveSurveyLocal to save all local assessments.
     *
     * @param view
     */
    public void saveLocal(View view) {
        view.setVisibility(View.GONE);
        Toast.makeText(MainActivity.this, "Saving...", Toast.LENGTH_LONG).show();
        Cursor resultSet = assessmentDatabase.rawQuery("Select * from assessments", null);
        resultSet.moveToFirst();
        saveSurveyLocal(resultSet.getString(0), resultSet.getString(1), resultSet.getString(2), resultSet.getString(3), resultSet.getString(4), resultSet.getString(5), resultSet.getString(6));
        while (resultSet.moveToNext()) {
            saveSurveyLocal(resultSet.getString(0), resultSet.getString(1), resultSet.getString(2), resultSet.getString(3), resultSet.getString(4), resultSet.getString(5), resultSet.getString(6));
        }

        resultSet.close();
    }

    /**
     * saveSurveyLocal
     * <p/>
     * Posts local assessment variables to webpage.
     *
     * @param id      The SQLite Database id of the assessment (used to delete if sent)
     * @param site
     * @param visit
     * @param answers
     * @param score
     * @param userID
     * @param url     the url to post to
     */
    private void saveSurveyLocal(final String id, final String site, final String visit, final String answers, final String score, final String userID, final String url) {
        RequestParams param = new RequestParams();
        param.put("txtVisit", visit);
        param.put("txtSite", site);
        param.put("txtAnswers", answers);
        param.put("txtScore", score);
        param.put("userID", userID);

        AsyncHttpClient client = new AsyncHttpClient();
        client.setEnableRedirects(true);
        client.post(url, param, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK
                assessmentDatabase.delete("assessments", "id=" + id, null);
                Cursor resultSet = assessmentDatabase.rawQuery("Select * from assessments", null);
                if (resultSet.getCount() == 0) {
                    Toast.makeText(MainActivity.this, "Assessments saved successfully!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                //((Button) MainActivity.this.postDataButton).setVisibility(View.VISIBLE);

                findViewById(R.id.saveLocal).setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Some assessments failed to save, try again later.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * populateSurvey
     * <p/>
     * Fill up the survey array with questions and answers.
     */
    private void populateSurvey() {
        //survey[survey][0 = baseline, 1 = week 1, 2 = week 2)
        survey[0][0] = "For the last 3 sessions of arm-related therapy you received from a therapist for your stroke, how do you feel about the arm exercises you have been doing?";
        survey[0][1] = "In the past week of arm-related therapy you have been doing as part of this research study, how do you feel about the arm exercises you have been doing?";
        survey[0][2] = "In the past six weeks of arm-related therapy you have been doing as part of this research study, how do you feel about the arm exercises you have been doing?";
        survey[0][3] = "Pleasurable?";
        survey[0][4] = "I find it unpleasurable";
        survey[0][5] = "I find it pleasurable";
        survey[0][6] = "Fun?";
        survey[0][7] = "It's no fun at all";
        survey[0][8] = "It's a lot of fun";
        survey[0][9] = "Pleasant?";
        survey[0][10] = "It's very unpleasant";
        survey[0][11] = "It's very pleasant";
        survey[0][12] = "Invigorating?";
        survey[0][13] = "It's not at all invigorating";
        survey[0][14] = "It's very invigorating";
        survey[0][15] = "Gratifying?";
        survey[0][16] = "It's not at all gratifying";
        survey[0][17] = "It's very gratifying";
        survey[0][18] = "Exhilarating?";
        survey[0][19] = "It's not at all exhilarating";
        survey[0][20] = "It's very exhilarating";
        survey[0][21] = "Stimulating?";
        survey[0][22] = "It's not at all stimulating";
        survey[0][23] = "It's very stimulating";
        survey[0][24] = "Refreshing?";
        survey[0][25] = "It's not at all refreshing";
        survey[0][26] = "It's very refreshing";

        survey[1][0] = "You and your therapist have decided on a goal for improving your arm functioning in the next 6 weeks. As you are answering the next set of questions, please think of this goal for improving your arm functioning.";
        survey[1][1] = "You and your therapist had recently decided on a goal for improving your arm functioning at the start of this study. As you are answering the next set of questions, please think of this goal for improving your arm functioning.";
        survey[1][2] = "At the start of this study, you and your therapist decided on a goal for improving your arm functioning. As you are answering the next set of questions, please think of this goal for improving your arm functioning.";
        survey[1][3] = "If I can't attain the goal one way, I look for alternative ways to still get to it.";
        survey[1][4] = "When I find it impossible to attain the goal, I reduce efforts toward that goal and put it out of my mind.";
        survey[1][5] = "Once I have decided on the goal, I always keep in mind it's benefits.";
        survey[1][6] = "I will stop thinking about the goal if it becomes unattainable and I will let it go.";
        survey[1][7] = "When I encounter problems, I don't give up until I solve them.";
        survey[1][8] = "If I cannot attain the goal, I will put effort into other meaningful goals.";
        survey[1][9] = "When I find it impossible to attain the goal, I try not to blame myself.";
        survey[1][10] = "When faced with a bad situation, I do what I can to change it for the better.";
        survey[1][11] = "When I cannot solve a problem myself, I ask others for help.";
        survey[1][12] = "If I cannot attain the goal, I think about other new goals to pursue.";
        survey[1][13] = "When I have decided on something, I avoid anything that could distract me.";
        survey[1][14] = "Even when everything seems to be going wrong, I can usually find a bright side to the situation.";

        survey[2][0] = "";
        survey[2][1] = "In the past week of arm-related therapy you have been doing as part of this research study, how satisfied are you with the therapy?";
        survey[2][2] = "In the past six weeks of arm-related therapy you have been doing as part of this research study, how satisfied are you with the therapy?";
        survey[2][3] = "I find the tasks/games:";
        survey[2][4] = "I find the tasks/games very unpleasurable";
        survey[2][5] = "I find the tasks/games very pleasurable";
        survey[2][6] = "I find it _______ to use the objects/devices.";
        survey[2][7] = "I find it very difficult to use the objects/devices";
        survey[2][8] = "I find it very easy to use the objects/devices";
        survey[2][9] = "I find the education module:";
        survey[2][10] = "I find the education module very unpleasurable";
        survey[2][11] = "I find the education module very pleasurable";
        survey[2][12] = "I find the home exercise module:";
        survey[2][13] = "I find the exercise module very unpleasurable";
        survey[2][14] = "I find the exercise module very pleasurable";
        survey[2][15] = "It's _______ to be compliant with the assignments.";
        survey[2][16] = "It's very difficult to be compliant with the assignments";
        survey[2][17] = "It's very easy to be compliant with the assignments";
        survey[2][18] = "The assignments are:";
        survey[2][19] = "The assignments are too challenging";
        survey[2][20] = "The assignments are not challenging at all";
        survey[2][21] = "The assignments:";
        survey[2][22] = "The assignments cause too much fatigue";
        survey[2][23] = "The assignments are not fatiguing at all";
        survey[2][24] = "The assignments are:";
        survey[2][25] = "The assignments are too confusing";
        survey[2][26] = "The assignments are not confusing at all";
        survey[2][27] = "The assignments are _______ in a timely manner.";
        survey[2][28] = "The assignments are very difficult to complete in a timely manner";
        survey[2][29] = "The assignments are easily completed in a timely manner";
        survey[2][30] = "I _____ this type of treatment to someone who had a stroke and was in a condition similar to my own.";
        survey[2][31] = "I would not recommend this type of treatment to someone who had a stroke and was in a condition similar to my own";
        survey[2][32] = "I would recommend this type of treatment to someone who had a stroke and was in a condition similar to my own";


    }

    /**
     * loadQuestion
     * <p/>
     * Displays the next question in the current survey.
     */
    private void loadQuestion() {
        int qIndex;
        switch (currentSurvey) {
            case 0:
                qIndex = (currentQ + 1) * 3;
                ((TextView) findViewById(R.id.survey_question)).setText(survey[currentSurvey][session] + "\n\nIs it " + survey[currentSurvey][qIndex]);
                ((TextView) findViewById(R.id.survey_answer1)).setText(survey[currentSurvey][qIndex + 1] + "\n1");
                ((TextView) findViewById(R.id.survey_answer7)).setText(survey[currentSurvey][qIndex + 2] + "\n7");
                break;

            case 1:
                qIndex = currentQ + 3;
                ((TextView) findViewById(R.id.survey_question)).setText(survey[currentSurvey][session] + "\n\n" + survey[currentSurvey][qIndex]);
                ((TextView) findViewById(R.id.survey_answer1)).setText("Strongly Disagree\n1");
                ((TextView) findViewById(R.id.survey_answer7)).setText("Strongly Agree\n7");
                break;

            case 2:
                qIndex = (currentQ + 1) * 3;
                ((TextView) findViewById(R.id.survey_question)).setText(survey[currentSurvey][session] + "\n\n" + survey[currentSurvey][qIndex]);
                ((TextView) findViewById(R.id.survey_answer1)).setText(survey[currentSurvey][qIndex + 1] + "\n1");
                ((TextView) findViewById(R.id.survey_answer7)).setText(survey[currentSurvey][qIndex + 2] + "\n7");
                break;
            default:
                break;
        }
    }

    /**
     * isNetworkAvailable
     * <p/>
     * Checks if there is a network connection.
     *
     * @return True if available, else False
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

}
