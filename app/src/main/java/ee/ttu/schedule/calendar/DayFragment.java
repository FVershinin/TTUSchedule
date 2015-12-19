package ee.ttu.schedule.calendar;

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import ee.ttu.schedule.calendar.CalendarController.EventInfo;
import ee.ttu.schedule.calendar.CalendarController.EventType;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.ViewSwitcher;
import android.widget.ViewSwitcher.ViewFactory;

import com.vadimstrukov.ttuschedule.R;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * This is the base class for Day and Week Activities.
 */
public class DayFragment extends Fragment implements CalendarController.EventHandler, ViewFactory {
    /**
     * The view id used for all the views we create. It's OK to have all child
     * views have the same ID. This ID is used to pick which view receives
     * focus when a view hierarchy is saved / restore
     */
    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    protected ProgressBar mProgressBar;
    protected ViewSwitcher mViewSwitcher;
    protected Animation mInAnimationForward;
    protected Animation mOutAnimationForward;
    protected Animation mInAnimationBackward;
    protected Animation mOutAnimationBackward;
    EventLoader mEventLoader;
    Calendar mSelectedDay;
    private int mNumDays;

    public static DayFragment newInstance(long timeMillis, int numOfDays) {
        Bundle args = new Bundle();
        args.putLong("TIME_MILLIS", timeMillis);
        args.putInt("NUM_OF_DAYS", numOfDays);
        DayFragment fragment = new DayFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Context context = getActivity();
        mNumDays = getArguments().getInt("NUM_OF_DAYS", 1);
        mSelectedDay = GregorianCalendar.getInstance(TimeZone.getDefault());
        if (getArguments().getLong("TIME_MILLIS", 0) != 0) {
            mSelectedDay.setTimeInMillis(getArguments().getLong("TIME_MILLIS"));
        }
        mInAnimationForward = AnimationUtils.loadAnimation(context, R.anim.slide_left_in);
        mOutAnimationForward = AnimationUtils.loadAnimation(context, R.anim.slide_left_out);
        mInAnimationBackward = AnimationUtils.loadAnimation(context, R.anim.slide_right_in);
        mOutAnimationBackward = AnimationUtils.loadAnimation(context, R.anim.slide_right_out);
        mEventLoader = new EventLoader(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_day, null);
        mViewSwitcher = (ViewSwitcher) v.findViewById(R.id.switcher);
        mViewSwitcher.setFactory(this);
        mViewSwitcher.getCurrentView().requestFocus();
        ((DayView) mViewSwitcher.getCurrentView()).updateTitle();
        return v;
    }

    public View makeView() {
        DayView view = new DayView(getActivity(), CalendarController
                .getInstance(getActivity()), mViewSwitcher, mEventLoader, mNumDays);
        view.setId(View.generateViewId());
        view.setLayoutParams(new ViewSwitcher.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mEventLoader.startBackgroundThread();
        eventsChanged();
        DayView view = (DayView) mViewSwitcher.getCurrentView();
        view.handleOnResume();
        view.restartCurrentTimeUpdates();
        view = (DayView) mViewSwitcher.getNextView();
        view.handleOnResume();
        view.restartCurrentTimeUpdates();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        long time = getSelectedTimeInMillis();
        if (time != -1) {
            outState.putLong(BUNDLE_KEY_RESTORE_TIME, time);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        DayView view = (DayView) mViewSwitcher.getCurrentView();
        view.cleanup();
        view = (DayView) mViewSwitcher.getNextView();
        view.cleanup();
        mEventLoader.stopBackgroundThread();
        // Stop events cross-fade animation
        view.stopEventsAnimation();
        ((DayView) mViewSwitcher.getNextView()).stopEventsAnimation();
    }

    void startProgressSpinner() {
        // start the progress spinner
        mProgressBar.setVisibility(View.VISIBLE);
    }

    void stopProgressSpinner() {
        // stop the progress spinner
        mProgressBar.setVisibility(View.GONE);
    }

    private void goTo(Calendar goToTime, boolean ignoreTime, boolean animateToday) {
        if (mViewSwitcher == null) {
            // The view hasn't been set yet. Just save the time and use it later.
            mSelectedDay = goToTime;
            return;
        }
        DayView currentView = (DayView) mViewSwitcher.getCurrentView();
        // How does goTo time compared to what's already displaying?
        int diff = currentView.compareToVisibleTimeRange(goToTime);
        if (diff == 0) {
            // In visible range. No need to switch view
            currentView.setSelected(goToTime, ignoreTime, animateToday);
        } else {
            // Figure out which way to animate
            if (diff > 0) {
                mViewSwitcher.setInAnimation(mInAnimationForward);
                mViewSwitcher.setOutAnimation(mOutAnimationForward);
            } else {
                mViewSwitcher.setInAnimation(mInAnimationBackward);
                mViewSwitcher.setOutAnimation(mOutAnimationBackward);
            }
            DayView next = (DayView) mViewSwitcher.getNextView();
            if (ignoreTime) {
                next.setFirstVisibleHour(currentView.getFirstVisibleHour());
            }
            next.setSelected(goToTime, ignoreTime, animateToday);
            next.reloadEvents();
            mViewSwitcher.showNext();
            next.requestFocus();
            next.updateTitle();
            next.restartCurrentTimeUpdates();
        }
    }

    /**
     * Returns the selected time in milliseconds. The milliseconds are measured
     * in UTC milliseconds from the epoch and uniquely specifies any selectable
     * time.
     *
     * @return the selected time in milliseconds
     */
    public long getSelectedTimeInMillis() {
        if (mViewSwitcher == null) {
            return -1;
        }
        DayView view = (DayView) mViewSwitcher.getCurrentView();
        if (view == null) {
            return -1;
        }
        return view.getSelectedTimeInMillis();
    }

    public void eventsChanged() {
        if (mViewSwitcher == null) {
            return;
        }
        DayView view = (DayView) mViewSwitcher.getCurrentView();
        view.clearCachedEvents();
        view.reloadEvents();
        view = (DayView) mViewSwitcher.getNextView();
        view.clearCachedEvents();
    }

    Event getSelectedEvent() {
        DayView view = (DayView) mViewSwitcher.getCurrentView();
        return view.getSelectedEvent();
    }

    boolean isEventSelected() {
        DayView view = (DayView) mViewSwitcher.getCurrentView();
        return view.isEventSelected();
    }

    Event getNewEvent() {
        DayView view = (DayView) mViewSwitcher.getCurrentView();
        return view.getNewEvent();
    }

    public DayView getNextView() {
        return (DayView) mViewSwitcher.getNextView();
    }

    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.EVENTS_CHANGED;
    }

    public void handleEvent(EventInfo msg) {
        if (msg.eventType == EventType.GO_TO) {
// TODO support a range of time
// TODO support event_id
// TODO support select message
            Calendar calendar = (Calendar) mSelectedDay.clone();
            calendar.setTimeInMillis(msg.selectedTime.toMillis(false));
            goTo(calendar, (msg.extraLong & CalendarController.EXTRA_GOTO_DATE) != 0,
                    (msg.extraLong & CalendarController.EXTRA_GOTO_TODAY) != 0);
        } else if (msg.eventType == EventType.EVENTS_CHANGED) {
            eventsChanged();
        }
    }
}
