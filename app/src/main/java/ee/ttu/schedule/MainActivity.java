package ee.ttu.schedule;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.SectionDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.vadimstrukov.ttuschedule.R;

import java.util.ArrayList;

import ee.ttu.schedule.fragment.AboutFragment;
import ee.ttu.schedule.fragment.ChangeScheduleFragment;
import ee.ttu.schedule.fragment.ScheduleFragment;

public class MainActivity extends AppCompatActivity implements Drawer.OnDrawerItemClickListener, Drawer.OnDrawerListener {

    private Fragment fragment = ScheduleFragment.newInstance(ScheduleFragment.TYPE_DAY_VIEW);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().add(R.id.fragment, fragment, ScheduleFragment.TAG).addToBackStack(ScheduleFragment.TAG).commit();
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ttu_schedule_ic);
        setSupportActionBar(toolbar);
        final ArrayList<IDrawerItem> drawerItems = new ArrayList<>();
        drawerItems.add(new SecondaryDrawerItem().withName(R.string.drawer_item_view_day).withIcon(GoogleMaterial.Icon.gmd_view_column).withIdentifier(0));
        drawerItems.add(new SecondaryDrawerItem().withName(R.string.drawer_item_view_three_days).withIcon(GoogleMaterial.Icon.gmd_view_day).withIdentifier(1));
        drawerItems.add(new SecondaryDrawerItem().withName(R.string.drawer_item_change_schedule).withIcon(GoogleMaterial.Icon.gmd_refresh).withIdentifier(2));
        drawerItems.add(new SectionDrawerItem().withName(R.string.drawer_item_section_preferences));
        drawerItems.add(new SecondaryDrawerItem().withName(R.string.drawer_item_about).withIcon(GoogleMaterial.Icon.gmd_info).withIdentifier(3));
        DrawerBuilder drawerBuilder = new DrawerBuilder(this);
        drawerBuilder.withToolbar(toolbar);
        drawerBuilder.withActionBarDrawerToggle(true);
        drawerBuilder.withHeader(R.layout.drawer_header);
        drawerBuilder.withDrawerItems(drawerItems);
        drawerBuilder.withOnDrawerItemClickListener(this);
        drawerBuilder.withOnDrawerListener(this);
        drawerBuilder.build();
    }

    @Override
    public boolean onItemClick(View view, int position, final IDrawerItem drawerItem) {
        assert drawerItem != null;
        switch (drawerItem.getIdentifier()) {
            case 0:
                fragment = getFragmentManager().findFragmentByTag(ScheduleFragment.TAG);
                ((ScheduleFragment)fragment).setTypeView(ScheduleFragment.TYPE_DAY_VIEW);
                break;
            case 1:
                fragment = getFragmentManager().findFragmentByTag(ScheduleFragment.TAG);
                ((ScheduleFragment)fragment).setTypeView(ScheduleFragment.TYPE_THREE_DAY_VIEW);
                break;
            case 2:
                fragment = new ChangeScheduleFragment();
                break;
            case 3:
                fragment = new AboutFragment();
                break;
        }
        return false;
    }

    @Override
    public void onDrawerOpened(View drawerView) {

    }

    @Override
    public void onDrawerClosed(View drawerView) {
        getFragmentManager().beginTransaction().replace(R.id.fragment, fragment).addToBackStack("test").commit();
    }

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {

    }
}
