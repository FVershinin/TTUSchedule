package ee.ttu.schedule.service.adapter;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

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
    private static final String URL = "http://test-patla4.rhcloud.com/api/v1";

    private static final int TIMEOUT = 15000;

    public static final String SYNC_TYPE = "sync_type";
    public static final int SYNC_GROUPS = 0;
    public static final int SYNC_EVENTS = 1;

    public SyncAdapter(Context context) {
        super(context, true);
    }

    @Override
    public void onPerformSync(Account account, final Bundle extras, String authority, final ContentProviderClient provider, SyncResult syncResult) {
        Log.d(TAG, "Syncing started");
        final int sync_type = extras.getInt(SYNC_TYPE, -1);
        ArrayList<ContentProviderOperation> operations;
        RequestQueue requestQueue = Volley.newRequestQueue(getContext());
        RequestFuture<JSONObject> future = RequestFuture.newFuture();

        try {
            switch (sync_type) {
                case SYNC_GROUPS:
                    JsonObjectRequest groupsRequest = new JsonObjectRequest(Request.Method.GET, String.format("%1$s/groups", URL), future, future);
                    groupsRequest.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                    requestQueue.add(groupsRequest);
                    operations = getGroups(future.get(), provider, syncResult);
                    provider.applyBatch(operations);
                    break;
                case SYNC_EVENTS:
                    JsonObjectRequest eventRequest = new JsonObjectRequest(Request.Method.GET, String.format("%1$s/schedule?group=%2$s", URL, extras.get("group")), future, future);
                    eventRequest.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                    requestQueue.add(eventRequest);
                    operations = getEvents(future.get(), provider, syncResult);
                    getContext().getContentResolver().applyBatch(CalendarContract.AUTHORITY, operations);
                    break;
                default:
                    return;
            }
            broadcastIntent(Constants.SYNC_STATUS_OK);
            PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putString("group", extras.getString("group")).commit();
        } catch (RemoteException | JSONException | InterruptedException | ExecutionException | OperationApplicationException e) {
            broadcastIntent(Constants.SYNC_STATUS_FAILED);
        }
    }

    private ArrayList<ContentProviderOperation> getEvents(JSONObject jsonObject, ContentProviderClient providerClient, SyncResult syncResult) throws RemoteException, JSONException {
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        Gson gson = new Gson();
        Event[] events = gson.fromJson(jsonObject.getJSONArray("events").toString(), Event[].class);
        getContext().getContentResolver().delete(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI, "TTU Schedule", "ee.ttu.schedule"), null, null);
        long cal_id = createCalender();
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putLong(CalendarContract.Calendars._ID, cal_id).commit();
        for (Event event : events) {
            operations.add(ContentProviderOperation.newInsert(asSyncAdapter(CalendarContract.Events.CONTENT_URI, "TTU Schedule", "ee.ttu.schedule"))
                    .withValue(CalendarContract.Events.DTSTART, event.getDateStart())
                    .withValue(CalendarContract.Events.DTEND, event.getDateEnd())
                    .withValue(CalendarContract.Events.TITLE, event.getSummary())
                    .withValue(CalendarContract.Events.EVENT_LOCATION, event.getLocation())
                    .withValue(CalendarContract.Events.DESCRIPTION, event.getDescription())
                    .withValue(CalendarContract.Events.CALENDAR_ID, cal_id).build());
            syncResult.stats.numInserts++;
        }
        return operations;
    }

    private ArrayList<ContentProviderOperation> getGroups(JSONObject jsonObject, ContentProviderClient providerClient, SyncResult syncResult) throws RemoteException, JSONException {
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        Gson gson = new Gson();
        String[] groups = gson.fromJson(jsonObject.getJSONArray("groups").toString(), String[].class);
        providerClient.delete(GroupContract.Group.CONTENT_URI, null, null);
        for (String group_name : groups) {
            operations.add(ContentProviderOperation.newInsert(GroupContract.Group.CONTENT_URI)
                    .withValue(GroupContract.Group.KEY_NAME, group_name).build());
            syncResult.stats.numInserts++;
        }
        return operations;
    }

    private Long createCalender() {
        final ContentValues contentValues = new ContentValues();
        contentValues.put(CalendarContract.Calendars.ACCOUNT_NAME, "TTU Schedule");
        contentValues.put(CalendarContract.Calendars.ACCOUNT_TYPE, "ee.ttu.schedule");
        contentValues.put(CalendarContract.Calendars.NAME, "ttu_schedule");
        contentValues.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "TTU Schedule");
        contentValues.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_READ);
        contentValues.put(CalendarContract.Calendars.OWNER_ACCOUNT, "TTU Schedule");
        contentValues.put(CalendarContract.Calendars.VISIBLE, 1);
        contentValues.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        final Uri uri = getContext().getContentResolver().insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI, "TTU Schedule", "ee.ttu.schedule"), contentValues);
        assert uri != null;
        return Long.parseLong(uri.getLastPathSegment());
    }

    private void broadcastIntent(int status) {
        Intent intent = new Intent();
        intent.setAction(Constants.SYNCHRONIZATION_ACTION);
        intent.putExtra(Constants.SYNC_STATUS, status);
        getContext().sendBroadcast(intent);
    }

    private static Uri asSyncAdapter(Uri uri, String account, String accountType) {
        return uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType).build();
    }
}