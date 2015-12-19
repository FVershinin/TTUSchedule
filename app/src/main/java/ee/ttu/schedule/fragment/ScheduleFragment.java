package ee.ttu.schedule.fragment;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncStatusObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.vadimstrukov.ttuschedule.R;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import ee.ttu.schedule.calendar.CalendarController;
import ee.ttu.schedule.calendar.DayFragment;
import ee.ttu.schedule.drawable.DayOfMonthDrawable;
import ee.ttu.schedule.provider.BaseContract;
import ee.ttu.schedule.utils.Constants;
import ee.ttu.schedule.utils.SyncUtils;

public class ScheduleFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, SyncStatusObserver {

    public static final int TYPE_DAY_VIEW = 1;
    public static final int TYPE_THREE_DAY_VIEW = 2;
    private static final String ARG_TYPE = "arg_type";
    private int WEEK_TYPE;

    private SwipeRefreshLayout swipeRefreshLayout;
    private Object syncObserverHandle;

    private SyncUtils syncUtils;

    private DayFragment fragment;

    public ScheduleFragment() {

    }

    public static ScheduleFragment newInstance(int type) {
        Bundle args = new Bundle();
        ScheduleFragment scheduleFragment = new ScheduleFragment();
        args.putInt(ARG_TYPE, type);
        scheduleFragment.setArguments(args);
        return scheduleFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        syncUtils = new SyncUtils(getActivity());
        if (getArguments() != null)
            WEEK_TYPE = getArguments().getInt(ARG_TYPE, TYPE_THREE_DAY_VIEW);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_schedule, container, false);
        swipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setEnabled(false);
        FragmentManager fragmentManager = getFragmentManager();
        switch (WEEK_TYPE) {
            case TYPE_DAY_VIEW:
                fragment = DayFragment.newInstance(GregorianCalendar.getInstance(TimeZone.getDefault()).getTimeInMillis(), 1);
                break;
            case TYPE_THREE_DAY_VIEW:
                fragment = DayFragment.newInstance(GregorianCalendar.getInstance(TimeZone.getDefault()).getTimeInMillis(), 3);
                break;
        }
        fragmentManager.beginTransaction().add(R.id.fragmentContainer, fragment).commit();
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        onStatusChanged(0);
        final int mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING | ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;
        syncObserverHandle = ContentResolver.addStatusChangeListener(mask, this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if(syncObserverHandle !=null){
            ContentResolver.removeStatusChangeListener(syncObserverHandle);
            syncObserverHandle = null;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        MenuItem menuItem = menu.findItem(R.id.action_today);
        setTodayIcon((LayerDrawable) menuItem.getIcon(), getActivity(), "EET");
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_today:
                final CalendarController.EventInfo eventInfo = new CalendarController.EventInfo();
                Time time = new Time("EET");
                time.set(GregorianCalendar.getInstance().getTimeInMillis());
                eventInfo.selectedTime = time;
                eventInfo.eventType = CalendarController.EventType.GO_TO;
                fragment.handleEvent(eventInfo);
                return true;
            case R.id.action_update:
                syncUtils.syncEvents(PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext()).getString(Constants.GROUP, null));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setTodayIcon(LayerDrawable icon, Context context, String timezone) {
        DayOfMonthDrawable today;
        // Reuse current drawable if possible
        Drawable currentDrawable = icon.findDrawableByLayerId(R.id.today_icon_day);
        if (currentDrawable != null && currentDrawable instanceof DayOfMonthDrawable) {
            today = (DayOfMonthDrawable) currentDrawable;
        } else {
            today = new DayOfMonthDrawable(context);
        }
        // Set the day and update the icon
        Calendar calendar = GregorianCalendar.getInstance(TimeZone.getDefault());
        today.setDayOfMonth(calendar.get(Calendar.DAY_OF_MONTH));
        icon.mutate();
        icon.setDrawableByLayerId(R.id.today_icon_day, today);
    }

    @Override
    public void onRefresh() {

    }

    @Override
    public void onStatusChanged(int which) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                NetworkInfo networkInfo = ((ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
                if(networkInfo == null){
                    ContentResolver.cancelSync(syncUtils.getAccount(), BaseContract.CONTENT_AUTHORITY);
                    Toast.makeText(getActivity(), getActivity().getString(R.string.err_network), Toast.LENGTH_SHORT).show();
                }
                boolean syncActive = ContentResolver.isSyncActive(syncUtils.getAccount(), BaseContract.CONTENT_AUTHORITY);
                boolean syncPending = ContentResolver.isSyncPending(syncUtils.getAccount(), BaseContract.CONTENT_AUTHORITY);
                swipeRefreshLayout.setRefreshing(syncActive || syncPending);
            }
        });
    }
}
