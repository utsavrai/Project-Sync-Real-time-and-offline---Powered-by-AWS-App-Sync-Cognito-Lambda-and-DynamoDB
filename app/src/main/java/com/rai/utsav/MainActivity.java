package com.rai.utsav;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.rai.utsav.R;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ForgotPasswordContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.NewPasswordContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.ForgotPasswordHandler;

import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final String TAG="MainActivity";


    private AlertDialog userDialog;
    private ProgressDialog waitDialog;

    // Screen fields
    private EditText inUsername;
    private EditText inPassword;

    //Continuations
    //private MultiFactorAuthenticationContinuation multiFactorAuthenticationContinuation;
    private ForgotPasswordContinuation forgotPasswordContinuation;
    private NewPasswordContinuation newPasswordContinuation;
    //private ChooseMfaContinuation mfaOptionsContinuation;

    // User Details
    private String username;
    private String password;

    // Mandatory overrides first
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);


        // Initialize application
        AppHelper.init(getApplicationContext());
        initApp();
        findCurrent();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 1:
                // Register user
                if(resultCode == RESULT_OK) {
                    String name = data.getStringExtra("name");
                    if (!name.isEmpty()) {
                        inUsername.setText(name);
                        inPassword.setText("");
                        inPassword.requestFocus();
                    }
                    String userPasswd = data.getStringExtra("password");
                    if (!userPasswd.isEmpty()) {
                        inPassword.setText(userPasswd);
                    }
                    if (!name.isEmpty() && !userPasswd.isEmpty()) {
                        // We have the user details, so sign in!
                        username = name;
                        password = userPasswd;
                        AppHelper.getPool().getUser(username).getSessionInBackground(authenticationHandler);
                    }
                }
                break;
            case 2:
                // Confirm register user
                if(resultCode == RESULT_OK) {
                    String name = data.getStringExtra("name");
                    if (!name.isEmpty()) {
                        inUsername.setText(name);
                        inPassword.setText("");
                        inPassword.requestFocus();
                    }
                }
                break;
            case 3:
                // Forgot password
                if(resultCode == RESULT_OK) {
                    String newPass = data.getStringExtra("newPass");
                    String code = data.getStringExtra("code");
                    if (newPass != null && code != null) {
                        if (!newPass.isEmpty() && !code.isEmpty()) {
                            showWaitDialog("Setting new password...");
                            forgotPasswordContinuation.setPassword(newPass);
                            forgotPasswordContinuation.setVerificationCode(code);
                            forgotPasswordContinuation.continueTask();
                        }
                    }
                }
                break;
            case 4:
                // User
                if(resultCode == RESULT_OK) {
                    clearInput();
                    String name = data.getStringExtra("TODO");
                    if(name != null) {
                        if (!name.isEmpty()) {
                            name.equals("exit");
                            onBackPressed();
                        }
                    }
                }
                break;
            case 6:
                //New password
                closeWaitDialog();
                Boolean continueSignIn = false;
                if (resultCode == RESULT_OK) {
                    continueSignIn = data.getBooleanExtra("continueSignIn", false);
                }
                if (continueSignIn) {
                    continueWithFirstTimeSignIn();
                }
                break;
        }
    }

    // App methods
    // Register user - start process
    public void signUp(View view) {
        signUpNewUser();
    }

    // Login if a user is already present
    public void logIn(View view) {
        signInUser();
    }

    // Forgot password processing
    public void forgotPassword(View view) {
        forgotpasswordUser();
    }


    private void signUpNewUser() {
        Intent registerActivity = new Intent(this, RegisterUser.class);
        startActivityForResult(registerActivity, 1);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void signInUser() {
        username = inUsername.getText().toString();
        if(username == null || username.length() < 1) {
            TextView label = (TextView) findViewById(R.id.textViewUserIdMessage);
            label.setText(inUsername.getHint()+" cannot be empty");
            inUsername.setBackground(getDrawable(R.drawable.text_border_error));
            return;
        }

        AppHelper.setUser(username);

        password = inPassword.getText().toString();
        if(password == null || password.length() < 1) {
            TextView label = (TextView) findViewById(R.id.textViewUserPasswordMessage);
            label.setText(inPassword.getHint()+" cannot be empty");
            inPassword.setBackground(getDrawable(R.drawable.text_border_error));
            return;
        }

        showWaitDialog("Signing in...");
        AppHelper.getPool().getUser(username).getSessionInBackground(authenticationHandler);
    }

    private void forgotpasswordUser() {
        username = inUsername.getText().toString();
        if(username == null) {
            TextView label = (TextView) findViewById(R.id.textViewUserIdMessage);
            label.setText(inUsername.getHint()+" cannot be empty");
            inUsername.setBackground(getDrawable(R.drawable.text_border_error));
            return;
        }

        if(username.length() < 1) {
            TextView label = (TextView) findViewById(R.id.textViewUserIdMessage);
            label.setText(inUsername.getHint()+" cannot be empty");
            inUsername.setBackground(getDrawable(R.drawable.text_border_error));
            return;
        }

        showWaitDialog("");
        AppHelper.getPool().getUser(username).forgotPasswordInBackground(forgotPasswordHandler);
    }

    private void getForgotPasswordCode(ForgotPasswordContinuation forgotPasswordContinuation) {
        this.forgotPasswordContinuation = forgotPasswordContinuation;
        Intent intent = new Intent(this, ForgotPasswordActivity.class);
        intent.putExtra("destination",forgotPasswordContinuation.getParameters().getDestination());
        intent.putExtra("deliveryMed", forgotPasswordContinuation.getParameters().getDeliveryMedium());
        startActivityForResult(intent, 3);
    }


    private void firstTimeSignIn() {
        Intent newPasswordActivity = new Intent(this, NewPassword.class);
        startActivityForResult(newPasswordActivity, 6);
    }

    private void continueWithFirstTimeSignIn() {
        newPasswordContinuation.setPassword(AppHelper.getPasswordForFirstTimeLogin());
        Map <String, String> newAttributes = AppHelper.getUserAttributesForFirstTimeLogin();
        if (newAttributes != null) {
            for(Map.Entry<String, String> attr: newAttributes.entrySet()) {
                Log.d(TAG, String.format(" -- Adding attribute: %s, %s", attr.getKey(), attr.getValue()));
                newPasswordContinuation.setUserAttribute(attr.getKey(), attr.getValue());
            }
        }
        try {
            newPasswordContinuation.continueTask();
        } catch (Exception e) {
            closeWaitDialog();
            TextView label = (TextView) findViewById(R.id.textViewUserIdMessage);
            label.setText("Sign-in failed");
            inPassword.setBackground(getDrawable(R.drawable.text_border_error));

            label = (TextView) findViewById(R.id.textViewUserIdMessage);
            label.setText("Sign-in failed");
            inUsername.setBackground(getDrawable(R.drawable.text_border_error));

            showDialogMessage("Sign-in failed", AppHelper.formatException(e));
        }
    }


    private void launchUser() {
        Intent k = new Intent(MainActivity.this, ListProjectsActivity.class);
        startActivity(k);
    }

    private void findCurrent() {
        CognitoUser user = AppHelper.getPool().getCurrentUser();
        username = user.getUserId();
        if(username != null) {
            AppHelper.setUser(username);
            inUsername.setText(user.getUserId());
            user.getSessionInBackground(authenticationHandler);
        }
    }

    private void getUserAuthentication(AuthenticationContinuation continuation, String username) {
        if(username != null) {
            this.username = username;
            AppHelper.setUser(username);
        }
        if(this.password == null) {
            inUsername.setText(username);
            password = inPassword.getText().toString();
            if(password == null) {
                TextView label = (TextView) findViewById(R.id.textViewUserPasswordMessage);
                label.setText(inPassword.getHint()+" enter password");
                inPassword.setBackground(getDrawable(R.drawable.text_border_error));
                return;
            }

            if(password.length() < 1) {
                TextView label = (TextView) findViewById(R.id.textViewUserPasswordMessage);
                label.setText(inPassword.getHint()+" enter password");
                inPassword.setBackground(getDrawable(R.drawable.text_border_error));
                return;
            }
        }
        AuthenticationDetails authenticationDetails = new AuthenticationDetails(this.username, password, null);
        continuation.setAuthenticationDetails(authenticationDetails);
        continuation.continueTask();
    }

    // initialize app
    private void initApp() {
        inUsername = (EditText) findViewById(R.id.editTextUserId);
        inUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if(s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewUserIdLabel);
                    label.setText(R.string.Username);
                    inUsername.setBackground(getDrawable(R.drawable.text_border_selector));
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                TextView label = (TextView) findViewById(R.id.textViewUserIdMessage);
                label.setText("");
            }

            @Override
            public void afterTextChanged(Editable s) {
                if(s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewUserIdLabel);
                    label.setText("");
                }
            }
        });

        inPassword = (EditText) findViewById(R.id.editTextUserPassword);
        inPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if(s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewUserPasswordLabel);
                    label.setText(R.string.Password);
                    inPassword.setBackground(getDrawable(R.drawable.text_border_selector));
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                TextView label = (TextView) findViewById(R.id.textViewUserPasswordMessage);
                label.setText("");
            }

            @Override
            public void afterTextChanged(Editable s) {
                if(s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewUserPasswordLabel);
                    label.setText("");
                }
            }
        });
    }


    // Callbacks
    ForgotPasswordHandler forgotPasswordHandler = new ForgotPasswordHandler() {
        @Override
        public void onSuccess() {
            closeWaitDialog();
            showDialogMessage("Password successfully changed!","");
            inPassword.setText("");
            inPassword.requestFocus();
        }

        @Override
        public void getResetCode(ForgotPasswordContinuation forgotPasswordContinuation) {
            closeWaitDialog();
            getForgotPasswordCode(forgotPasswordContinuation);
        }

        @Override
        public void onFailure(Exception e) {
            closeWaitDialog();
            showDialogMessage("Forgot password failed", AppHelper.formatException(e));
        }
    };

    //
    AuthenticationHandler authenticationHandler = new AuthenticationHandler() {
        @Override
        public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice device) {
            Log.d(TAG, " -- Auth Success");
            AppHelper.setCurrSession(cognitoUserSession);
            AppHelper.newDevice(device);
            closeWaitDialog();
            launchUser();
        }

        @Override
        public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String username) {
            closeWaitDialog();
            Locale.setDefault(Locale.US);
            getUserAuthentication(authenticationContinuation, username);
        }
        @Override
        public void getMFACode(MultiFactorAuthenticationContinuation multiFactorAuthenticationContinuation) {
            multiFactorAuthenticationContinuation.continueTask();
        }
        @Override
        public void onFailure(Exception e) {
            closeWaitDialog();
            TextView label = (TextView) findViewById(R.id.textViewUserIdMessage);
            label.setText("Sign-in failed");
            inPassword.setBackground(getDrawable(R.drawable.text_border_error));

            label = (TextView) findViewById(R.id.textViewUserIdMessage);
            label.setText("Sign-in failed");
            inUsername.setBackground(getDrawable(R.drawable.text_border_error));

            showDialogMessage("Sign-in failed", AppHelper.formatException(e));
        }
        @Override
        public void authenticationChallenge(ChallengeContinuation continuation) {
            /**
             * For Custom authentication challenge, implement your logic to present challenge to the
             * user and pass the user's responses to the continuation.
             */
            if ("NEW_PASSWORD_REQUIRED".equals(continuation.getChallengeName())) {
                // This is the first sign-in attempt for an admin created user
                newPasswordContinuation = (NewPasswordContinuation) continuation;
                AppHelper.setUserAttributeForDisplayFirstLogIn(newPasswordContinuation.getCurrentUserAttributes(),
                        newPasswordContinuation.getRequiredAttributes());
                closeWaitDialog();
                firstTimeSignIn();
            } else if ("SELECT_MFA_TYPE".equals(continuation.getChallengeName())) {
                closeWaitDialog();
            }
        }

    };



    private void clearInput() {
        if(inUsername == null) {
            inUsername = (EditText) findViewById(R.id.editTextUserId);
        }

        if(inPassword == null) {
            inPassword = (EditText) findViewById(R.id.editTextUserPassword);
        }

        inUsername.setText("");
        inUsername.requestFocus();
        inUsername.setBackground(getDrawable(R.drawable.text_border_selector));
        inPassword.setText("");
        inPassword.setBackground(getDrawable(R.drawable.text_border_selector));
    }

    private void showWaitDialog(String message) {
        closeWaitDialog();
        waitDialog = new ProgressDialog(this);
        waitDialog.setTitle(message);
        waitDialog.show();
    }

    private void showDialogMessage(String title, String body) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(body).setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    userDialog.dismiss();
                } catch (Exception e) {
                    //
                }
            }
        });
        userDialog = builder.create();
        userDialog.show();
    }

    private void closeWaitDialog() {
        try {
            waitDialog.dismiss();
        }
        catch (Exception e) {
            //
        }
    }
}
