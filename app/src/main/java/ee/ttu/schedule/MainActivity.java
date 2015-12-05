package ee.ttu.schedule;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vadimstrukov.ttuschedule.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ee.ttu.schedule.model.Subject;
import ee.ttu.schedule.provider.EventContract;
import ee.ttu.schedule.utils.Constants;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Button getScheduleButton;
    private AutoCompleteTextView groupField;
    private TextInputLayout inputLayoutGroup;
    private String group;
    private View loading_panel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Account account = new Account(getString(R.string.app_name), "ee.ttu.schedule");
        AccountManager accountManager = (AccountManager) getApplicationContext().getSystemService(ACCOUNT_SERVICE);
        if(accountManager.addAccountExplicitly(account, null, null)){
            ContentResolver.setIsSyncable(account, "ee.ttu.schedule", 1);
        }
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(account, "ee.ttu.schedule", bundle);


        final List<String> groupList = new ArrayList<>();
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest jsObjRequest = new JsonArrayRequest(Request.Method.GET, Constants.URL + "/groups",
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        for (int i = 0; i < response.length(); i++) {
                            try {
                                groupList.add(response.get(i).toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        queue.add(jsObjRequest);
                setContentView(R.layout.start_activity);
                groupField = (AutoCompleteTextView) findViewById(R.id.input_group);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, groupList);
                groupField.setAdapter(adapter);
                inputLayoutGroup = (TextInputLayout) findViewById(R.id.input_layout_group);
                getScheduleButton = (Button) findViewById(R.id.btn_get);
                groupField.addTextChangedListener(new MyTextWatcher(groupField));
                loading_panel = findViewById(R.id.loadingPanel);
                loading_panel.setVisibility(View.INVISIBLE);
                getScheduleButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        submitForm();
                    }
                });
    }


    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void startActivity(Class<?> cls, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(MainActivity.this, cls);
        startActivity(intent);
        finish();
    }

    private void submitForm() {
        if (!validateName()) {
            return;
        }
        loading_panel.setVisibility(View.VISIBLE);
        InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(groupField.getWindowToken(), 0);

        Toast.makeText(getApplicationContext(), "Schedule loading...", Toast.LENGTH_SHORT).show();
        group = groupField.getText().toString().toUpperCase();
        RequestQueue queue = Volley.newRequestQueue(this);
        String request = Constants.URL + "/schedule?groups=" + group;
        JsonObjectRequest jsRequest = new JsonObjectRequest(Request.Method.GET, request,


                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        TypeToken typeToken = new TypeToken<Map<String, List<Subject>>>() {
                        };
                        Map<String, List<Subject>> subjectMap = new GsonBuilder().create().fromJson(response.toString(), typeToken.getType());
                        for(Subject subject : subjectMap.get(group)) {
                            ContentValues contentValues = new ContentValues();
                            contentValues.put(EventContract.EventColumns.KEY_DT_START, subject.getDateStart());
                            contentValues.put(EventContract.EventColumns.KEY_DT_END, subject.getDateEnd());
                            contentValues.put(EventContract.EventColumns.KEY_DESCRIPTION, subject.getDescription());
                            contentValues.put(EventContract.EventColumns.KEY_LOCATION, subject.getLocation());
                            contentValues.put(EventContract.EventColumns.KEY_SUMMARY, subject.getSummary());
                            getContentResolver().insert(EventContract.Event.CONTENT_URI, contentValues);
                        }
                        Intent intent = new Intent(MainActivity.this, DrawerActivity.class);
                        loading_panel.setVisibility(View.INVISIBLE);
                        startActivity(intent);
                        finish();
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(MainActivity.this, "Failure!", Toast.LENGTH_SHORT).show();
                loading_panel.setVisibility(View.INVISIBLE);

            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("groups", "RDIR51");
                return params;
            }
        };
        queue.add(jsRequest);
    }


    private boolean validateName() {
        if (groupField.getText().toString().trim().isEmpty()) {
            inputLayoutGroup.setError(getString(R.string.err_msg_group));
            requestFocus(groupField);
            return false;
        } else if (!isOnline()) {
            inputLayoutGroup.setError(getString(R.string.err_network));
            requestFocus(groupField);
            return false;
        } else {
            inputLayoutGroup.setErrorEnabled(false);
        }

        return true;
    }

    private void requestFocus(View view) {
        if (view.requestFocus()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private class MyTextWatcher implements TextWatcher {

        private View view;

        private MyTextWatcher(View view) {
            this.view = view;
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void afterTextChanged(Editable editable) {
            switch (view.getId()) {
                case R.id.input_group:
                    validateName();
                    break;
            }
        }
    }
}
