package com.twilio.example.quickstart;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.twilio.example.quickstart.SignalPhone.LoginListener;
import com.twilio.signal.ConversationsClient;

public class SignalPhoneActivity extends Activity implements LoginListener {

	private static final String DEFAULT_CLIENT_NAME = "alice";
	private static final String TAG = "SignalPhoneActivity";
	
	public static final String CONVERSATION_PARTICIPANT = "conversation_participant";
	public static final String CONVERSATION_ACTION = "conversation_action";
	public static final String CONVERSATION_ACTION_CALL = "call";
	public static final String CONVERSATION_ACTION_ACCEPT_INCOMING = "accept_incoming";

	SignalPhone phone;
	private EditText clientNameTextBox;
	private EditText inviteText;
	private Button login;
	private Button logout;
	private Button addParticipant;

	private LinearLayout invite;
	private LinearLayout loginUser;
	private ProgressDialog progressDialog;
	private static final Handler handler = new Handler();
	private AlertDialog incomingAlert;


	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);
		this.loginUser = (LinearLayout)findViewById(R.id.loginUser);
		this.clientNameTextBox = (EditText)findViewById(R.id.client_name);
		this.clientNameTextBox.setText(DEFAULT_CLIENT_NAME);
		this.login = (Button)findViewById(R.id.register);
		this.inviteText = (EditText)findViewById(R.id.invite);
		this.phone = SignalPhone.getInstance(getApplicationContext());
		this.phone.setListeners(this);
		this.login.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				new LoginAsyncTask().execute();
			}
		});
		this.invite = (LinearLayout)findViewById(R.id.inviteParticipant);
		this.addParticipant = (Button)findViewById(R.id.plus);
		this.addParticipant.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String participant = inviteText.getText().toString();
				SignalPhoneActivity.this.call(participant);
			}
		});
		this.logout = (Button)findViewById(R.id.logout);
		this.logout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SignalPhoneActivity.this.phone.logout();
			}
		});
	}
	

	@Override
	public void onLoginStarted() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(SignalPhoneActivity.this.progressDialog.isShowing()){
					SignalPhoneActivity.this.progressDialog.setMessage("Registering ...");
				}

			}
		});
	}


	@Override
	public void onLoginFinished() {
		/*
		 * Intent intent = new Intent(this, ConversationActivity.class);
		 * this.startActivity(intent);
		 */

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(SignalPhoneActivity.this.progressDialog.isShowing()){
					SignalPhoneActivity.this.progressDialog.dismiss();
				}
				SignalPhoneActivity.this.invite.setVisibility(View.VISIBLE);
				SignalPhoneActivity.this.loginUser.setVisibility(View.GONE);

			}
		});

	}


	@Override
	public void onLoginError(String error) {
		//this.onResume();
		Log.e(TAG, "Login failed:"+error);
	}


	@Override
	public void onLogoutFinished() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				SignalPhoneActivity.this.loginUser.setVisibility(View.VISIBLE);
				SignalPhoneActivity.this.invite.setVisibility(View.GONE);
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		Intent intent = getIntent();
		if(intent != null && getIntent().getParcelableExtra(ConversationsClient.EXTRA_DEVICE) != null) {
			//showIncomingAlert();
		}
	}

	private class LoginAsyncTask extends AsyncTask<String, Void, String> {

		@Override
	    protected void onPreExecute() {
	        super.onPreExecute();
	        SignalPhoneActivity.this.progressDialog = ProgressDialog.show(SignalPhoneActivity.this, "",
	                "Logging in. Please wait...", true);
		}

		@Override
		protected String doInBackground(String... params) {
			SignalPhoneActivity.this.phone.login(clientNameTextBox.getText().toString());
			return null;
		}

	}
	
	private void call(String participant) {
		Intent intent = new Intent(this, ConversationActivity.class);
		intent.putExtra(CONVERSATION_PARTICIPANT, participant);
		intent.putExtra(CONVERSATION_ACTION, CONVERSATION_ACTION_CALL);
		startActivity(intent);
	}
	
	private void accept(String from) {
		Intent intent = new Intent(this, ConversationActivity.class);
		intent.putExtra(CONVERSATION_ACTION, CONVERSATION_ACTION_ACCEPT_INCOMING);
		intent.putExtra(CONVERSATION_PARTICIPANT, from);
		startActivity(intent);
	}


	 private void showIncomingAlert(final String from)
	    {
	        handler.post(new Runnable()
	        {
	            @Override
	            public void run()
	            {
	                if (incomingAlert == null) {
	                    incomingAlert = new AlertDialog.Builder(SignalPhoneActivity.this)
	                        .setTitle(R.string.incoming_call)
	                        .setMessage(R.string.incoming_call_message)
	                        .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener()
	                        {
	                            @Override
	                            public void onClick(DialogInterface dialog, int which)
	                            {
//	                                phone.accept();
//	                                incomingAlert = null;
	                            	accept(from);
	                            	//startActivity(new Intent(getBaseContext(), ConversationActivity.class));
	                            }
	                        })
	                        .setNegativeButton(R.string.reject, new DialogInterface.OnClickListener()
	                        {
	                            @Override
	                            public void onClick(DialogInterface dialog, int which)
	                            {
	                                //phone.ignoreIncomingConnection();
	                            	phone.reject(from);
	                                incomingAlert = null;
	                            }
	                        })
	                        .setOnCancelListener(new DialogInterface.OnCancelListener()
	                        {
	                            @Override
	                            public void onCancel(DialogInterface dialog)
	                            {
	                                phone.ignore(from);
	                            }
	                        })
	                        .create();
	                    incomingAlert.show();
	                }
	            }
	        });
	    }


	@Override
	public void onIncomingCall(String from) {
		showIncomingAlert(from);
		
	}
}
