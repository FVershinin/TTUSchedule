package ee.ttu.schedule.utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

import com.vadimstrukov.ttuschedule.R;

import ee.ttu.schedule.service.adapter.SyncAdapter;

public class SyncUtils {
    private final Account account;

    public SyncUtils(Context context) {
        account = new Account(context.getString(R.string.app_name), "ee.ttu.schedule");
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        if (accountManager.addAccountExplicitly(account, null, null)) {
            ContentResolver.setIsSyncable(account, "ee.ttu.schedule", 1);
        }
    }

    public void syncGroups(){
        Bundle bundle = new Bundle();
        bundle.putInt(SyncAdapter.SYNC_TYPE, SyncAdapter.SYNC_LIST_OF_SCHEDULES);
        sync(bundle);
    }

    public void syncEvents(String group){
        Bundle bundle = new Bundle();
        bundle.putInt(SyncAdapter.SYNC_TYPE, SyncAdapter.SYNC_SCHEDULE);
        bundle.putString("group", group);
        sync(bundle);
    }

    private void sync(Bundle bundle){
        Bundle coreBundle = new Bundle(bundle);
        coreBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        coreBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(account, "ee.ttu.schedule", coreBundle);
    }

    public Account getAccount() {
        return account;
    }
}
