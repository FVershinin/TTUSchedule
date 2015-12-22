package ee.ttu.schedule.service.adapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import ee.ttu.schedule.model.Event;
import ee.ttu.schedule.provider.EventContract;
import ee.ttu.schedule.provider.GroupContract;
import ee.ttu.schedule.utils.Constants;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private final String TAG = this.getClass().getSimpleName();
    private static final String URL = "http://vkttu.jelastic.planeetta.net/api/v2";

    private static final int TIMEOUT = 15000;

    public static final String SYNC_TYPE = "sync_type";
    public static final int SYNC_LIST_OF_SCHEDULES = 0;
    public static final int SYNC_SCHEDULE = 1;

    public SyncAdapter(Context context) {
        super(context, true);
    }

    @Override
    public void onPerformSync(Account account, final Bundle extras, String authority, final ContentProviderClient provider, SyncResult syncResult) {
        Log.d(TAG, "Syncing started");
        final int sync_type = extras.getInt(SYNC_TYPE, -1);
        Gson gson = new Gson();
        RequestQueue requestQueue = Volley.newRequestQueue(getContext());
        RequestFuture<JSONObject> jsonObjectFuture = RequestFuture.newFuture();
        RequestFuture<JSONArray> jsonArrayFuture = RequestFuture.newFuture();
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();

        try {
            switch (sync_type) {
                case SYNC_LIST_OF_SCHEDULES:
                    JsonArrayRequest eventRequest = new JsonArrayRequest(Request.Method.GET, URL + "/schedules", jsonArrayFuture, jsonArrayFuture);
                    eventRequest.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                    requestQueue.add(eventRequest);
                    provider.delete(GroupContract.Group.CONTENT_URI, null, null);
                    String[] groups = gson.fromJson(jsonArrayFuture.get().toString(), String[].class);
                    for (String group : groups) {
                        operations.add(ContentProviderOperation.newInsert(GroupContract.Group.CONTENT_URI)
                                .withValue(GroupContract.Group.KEY_NAME, group).build());
                        syncResult.stats.numInserts++;
                    }
                    break;
                case SYNC_SCHEDULE:
                    JsonObjectRequest groupsRequest = new JsonObjectRequest(Request.Method.GET, URL + "/schedules/" + extras.get("group"), jsonObjectFuture, jsonArrayFuture);
                    groupsRequest.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                    requestQueue.add(groupsRequest);
                    Event[] events = gson.fromJson(jsonObjectFuture.get().getJSONArray("events").toString(), Event[].class);
                    provider.delete(EventContract.Event.CONTENT_URI, null, null);
                    for (Event event : events) {
                        operations.add(ContentProviderOperation.newInsert(EventContract.Event.CONTENT_URI)
                                .withValue(EventContract.EventColumns.KEY_DT_START, event.getDateStart())
                                .withValue(EventContract.EventColumns.KEY_DT_END, event.getDateEnd())
                                .withValue(EventContract.EventColumns.KEY_DESCRIPTION, event.getDescription())
                                .withValue(EventContract.EventColumns.KEY_LOCATION, event.getLocation())
                                .withValue(EventContract.EventColumns.KEY_SUMMARY, event.getSummary()).build());
                        syncResult.stats.numInserts++;
                    }
                    break;
                default:
                    return;
            }
            provider.applyBatch(operations);
            broadcastIntent(Constants.SYNC_STATUS_OK);
            PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putString("group", extras.getString("group")).commit();
        }
        catch (RemoteException | JSONException | InterruptedException | ExecutionException | OperationApplicationException e) {
            broadcastIntent(Constants.SYNC_STATUS_FAILED);
        }
    }

    private void broadcastIntent(int status) {
        Intent intent = new Intent();
        intent.setAction(Constants.SYNCHRONIZATION_ACTION);
        intent.putExtra(Constants.SYNC_STATUS, status);
        getContext().sendBroadcast(intent);
    }
}