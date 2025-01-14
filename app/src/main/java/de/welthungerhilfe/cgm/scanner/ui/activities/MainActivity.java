/*
 *  Child Growth Monitor - quick and accurate data on malnutrition
 *  Copyright (c) $today.year Welthungerhilfe Innovation
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.welthungerhilfe.cgm.scanner.ui.activities;

import android.Manifest;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.SearchView;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.appeaser.sublimepickerlibrary.datepicker.SelectedDate;
import com.appeaser.sublimepickerlibrary.recurrencepicker.SublimeRecurrencePicker;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.ViewHolder;

import java.io.File;
import java.util.Calendar;
import java.util.List;

import de.welthungerhilfe.cgm.scanner.AppController;
import de.welthungerhilfe.cgm.scanner.R;
import de.welthungerhilfe.cgm.scanner.databinding.ActivityMainBinding;
import de.welthungerhilfe.cgm.scanner.databinding.ContentMainBinding;
import de.welthungerhilfe.cgm.scanner.datasource.models.Loc;
import de.welthungerhilfe.cgm.scanner.datasource.repository.PersonRepository;
import de.welthungerhilfe.cgm.scanner.datasource.viewmodel.PersonListViewModel;
import de.welthungerhilfe.cgm.scanner.network.service.DeviceService;
import de.welthungerhilfe.cgm.scanner.network.service.WifiStateChangereceiverHelperService;
import de.welthungerhilfe.cgm.scanner.network.syncdata.MeasureNotification;
import de.welthungerhilfe.cgm.scanner.ui.adapters.RecyclerPersonAdapter;
import de.welthungerhilfe.cgm.scanner.ui.dialogs.ConfirmDialog;
import de.welthungerhilfe.cgm.scanner.ui.dialogs.DateRangePickerDialog;
import de.welthungerhilfe.cgm.scanner.ui.fragments.DeviceCheckFragment;
import de.welthungerhilfe.cgm.scanner.hardware.io.LocalPersistency;
import de.welthungerhilfe.cgm.scanner.hardware.io.LogFileUtils;
import de.welthungerhilfe.cgm.scanner.utils.SessionManager;
import de.welthungerhilfe.cgm.scanner.datasource.models.Person;
import de.welthungerhilfe.cgm.scanner.AppConstants;
import de.welthungerhilfe.cgm.scanner.utils.Utils;
import de.welthungerhilfe.cgm.scanner.network.syncdata.SyncingWorkManager;

import static de.welthungerhilfe.cgm.scanner.ui.activities.DeviceCheckActivity.KEY_LAST_DEVICE_CHECK_ISSUES;

public class MainActivity extends BaseActivity implements RecyclerPersonAdapter.OnPersonDetail, DateRangePickerDialog.Callback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final long REQUEST_DEVICE_CHECK_TIME = 1000 * 3600 * 12; //12h

    private static final int STD_TEST_DEACTIVE = 1;


    private PersonListViewModel viewModel;

    public void createData(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            runnable = () -> startActivity(new Intent(MainActivity.this, QRScanActivity.class).putExtra(AppConstants.ACTIVITY_BEHAVIOUR_TYPE, AppConstants.CONSENT_CAPTURED_REQUEST));
            addResultListener(PERMISSION_CAMERA, listener);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
        } else {
            startActivity(new Intent(MainActivity.this, QRScanActivity.class).putExtra(AppConstants.ACTIVITY_BEHAVIOUR_TYPE, AppConstants.CONSENT_CAPTURED_REQUEST));
        }
    }

    RecyclerPersonAdapter adapterData;
    LinearLayoutManager lytManager;

    private ActionBarDrawerToggle mDrawerToggle;

    private DialogPlus sortDialog;

    private Runnable runnable;
    private SessionManager session;

    private ActivityMainBinding activityMainBinding;

    private ContentMainBinding contentMainBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        session = new SessionManager(MainActivity.this);
        LogFileUtils.startSession(MainActivity.this, session);
        LogFileUtils.logInfo(TAG, "CGM-Scanner " + Utils.getAppVersion(this) + " started");
        viewModel = ViewModelProviders.of(this).get(PersonListViewModel.class);

        if (session.getStdTestQrCode() != null) {
            if (!Utils.isValidateStdTestQrCode(session.getStdTestQrCode())) {
                session.setStdTestQrCode(null);
                showStdTestButtonInMenu(false);
            }
        }
        final Observer<List<Person>> observer = list -> {
            Log.e("PersonRecycler", "Observer called");

            if (lytManager.getItemCount() == 0 && list != null && list.size() == 0) {
                activityMainBinding.contentMain.lytNoPerson.setVisibility(View.VISIBLE);
            } else {
                activityMainBinding.contentMain.lytNoPerson.setVisibility(View.GONE);
                adapterData.clear();
                adapterData.addPersons(list);
            }
        };
        viewModel.getPersonListLiveData().observe(this, observer);

        setupSidemenu();
        setupActionBar();
        setupSortDialog();

        lytManager = new LinearLayoutManager(MainActivity.this);
        activityMainBinding.contentMain.recyclerData.setLayoutManager(lytManager);
        activityMainBinding.contentMain.recyclerData.setItemAnimator(new DefaultItemAnimator());
        activityMainBinding.contentMain.recyclerData.setHasFixedSize(true);


        adapterData = new RecyclerPersonAdapter(this, activityMainBinding.contentMain.recyclerData, viewModel);
        adapterData.setPersonDetailListener(this);
        activityMainBinding.contentMain.recyclerData.setAdapter(adapterData);

        startService(new Intent(this, DeviceService.class));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!WifiStateChangereceiverHelperService.isServiceRunning) {
                startForegroundService(new Intent(this, WifiStateChangereceiverHelperService.class));
            }
        }

        File log = new File(AppController.getInstance().getPublicAppDirectory(MainActivity.this)
                + "/cgm");
        showStdTestButtonInMenu(false);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (adapterData != null)
            adapterData.notifyDataSetChanged();
    }

    private void setupSidemenu() {
        activityMainBinding.navMenu.setNavigationItemSelectedListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.menuUploadManager:
                    startActivity(new Intent(MainActivity.this, UploadManagerActivity.class));
                    break;
                case R.id.menuDeviceCheck:
                    startActivity(new Intent(MainActivity.this, DeviceCheckActivity.class));
                    break;
                case R.id.menuTutorial:
                    Intent intent = new Intent(MainActivity.this, TutorialActivity.class);
                    intent.putExtra(AppConstants.EXTRA_TUTORIAL_AGAIN, true);
                    startActivity(intent);
                    break;
                case R.id.menuSettings:
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    break;
                case R.id.menuLogout:
                    session.setSigned(false);
                    session.setCurrentLogFilePath(null);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (WifiStateChangereceiverHelperService.isServiceRunning) {
                            startForegroundService(new Intent(this, WifiStateChangereceiverHelperService.class)
                                    .putExtra(AppConstants.STOP_SERVICE, true));
                        }
                    }
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                    break;
                case R.id.menuQuitStdTest:
                    showConfirmDialog(R.string.std_test_deactivate, STD_TEST_DEACTIVE);
                    break;
            }
            activityMainBinding.drawer.closeDrawers();
            return true;
        });
        View headerView = activityMainBinding.navMenu.getHeaderView(0);
        TextView txtUsername = headerView.findViewById(R.id.txtUsername);
        txtUsername.setText(session.getUserEmail());
    }

    private void setupActionBar() {
        activityMainBinding.contentMain.searchbar.setVisibility(View.GONE);
        setSupportActionBar(activityMainBinding.contentMain.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setTitle(R.string.title_scans);
            invalidateOptionsMenu();
        }

        mDrawerToggle = new ActionBarDrawerToggle(this, activityMainBinding.drawer, activityMainBinding.contentMain.toolbar, R.string.drawer_open, R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
            }

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }
        };

        activityMainBinding.drawer.addDrawerListener(mDrawerToggle);
    }

    private void openSearchBar() {
        activityMainBinding.contentMain.searchbar.setVisibility(View.VISIBLE);
        setSupportActionBar(activityMainBinding.contentMain.searchbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        invalidateOptionsMenu();

        activityMainBinding.contentMain.searchview.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {

                viewModel.setFilterQuery(query);
                return false;
            }
        });

        ImageView closeButton = activityMainBinding.contentMain.searchview.findViewById(R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            activityMainBinding.contentMain.searchview.setQuery("", false);

            viewModel.clearFilterOwn();
        });

        ImageView magImage = activityMainBinding.contentMain.searchview.findViewById(R.id.search_mag_icon);
        magImage.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
    }

    private void setupSortDialog() {
        sortDialog = DialogPlus.newDialog(MainActivity.this)
                .setContentHolder(new ViewHolder(R.layout.dialog_sort))
                .setCancelable(true)
                .setInAnimation(R.anim.abc_fade_in)
                .setOutAnimation(R.anim.abc_fade_out)
                .setOnClickListener((dialog, view) -> {

                    switch (view.getId()) {
                        case R.id.rytFilterData:
                            viewModel.setFilterOwn();
                            break;
                        case R.id.rytFilterDate:
                            doFilterByDate();
                            break;
                        case R.id.rytFilterLocation:
                            doFilterByLocation();
                            break;
                        case R.id.rytFilterClear:
                            viewModel.setFilterNo();
                            break;
                        case R.id.rytSortDate:
                            viewModel.setSortType(AppConstants.SORT_DATE);
                            break;
                        case R.id.rytSortLocation:
                            adapterData.clear();
                            Loc loc = Utils.getLastKnownLocation(this);
                            if (loc != null) {
                                viewModel.setLocation(loc);
                                viewModel.setSortType(AppConstants.SORT_LOCATION);
                            }
                            break;
                        case R.id.rytSortWasting:

                            viewModel.setSortType(AppConstants.SORT_WASTING);
                            break;
                        case R.id.rytSortStunting:

                            viewModel.setSortType(AppConstants.SORT_STUNTING);
                            break;
                    }

                    dialog.dismiss();
                })
                .create();

        viewModel.getPersonFilterLiveData().observe(this, filter -> {
            View view = sortDialog.getHolderView();

            //filter check icon
            boolean anyFilter = filter.isOwn() || filter.isDate() || filter.isLocation();
            view.findViewById(R.id.imgFilterData).setVisibility(filter.isOwn() ? View.VISIBLE : View.INVISIBLE);
            view.findViewById(R.id.imgFilterDate).setVisibility(filter.isDate() ? View.VISIBLE : View.INVISIBLE);
            view.findViewById(R.id.imgFilterLocation).setVisibility(filter.isLocation() ? View.VISIBLE : View.INVISIBLE);
            view.findViewById(R.id.imgFilterClear).setVisibility(anyFilter ? View.INVISIBLE : View.VISIBLE);

            //sort check icon
            int sort = filter.getSortType();
            view.findViewById(R.id.imgSortDate).setVisibility(sort == AppConstants.SORT_DATE ? View.VISIBLE : View.INVISIBLE);
            view.findViewById(R.id.imgSortLocation).setVisibility(sort == AppConstants.SORT_LOCATION ? View.VISIBLE : View.INVISIBLE);
            view.findViewById(R.id.imgSortWasting).setVisibility(sort == AppConstants.SORT_WASTING ? View.VISIBLE : View.INVISIBLE);
            view.findViewById(R.id.imgSortStunting).setVisibility(sort == AppConstants.SORT_STUNTING ? View.VISIBLE : View.INVISIBLE);

            //set date info
            if (filter.isDate()) {
                int diff = (int) Math.ceil((double) (filter.getToDate() - filter.getFromDate()) / 1000 / 3600 / 24);
                TextView txtView = view.findViewById(R.id.txtFilterDate);
                txtView.setText(getResources().getString(R.string.last_days, diff));
            } else {
                TextView txtView = view.findViewById(R.id.txtFilterDate);
                txtView.setText(getResources().getString(R.string.last_days).replace("%1$d", "x"));
            }

            //set location info
            if (filter.isLocation()) {
                TextView txtView = sortDialog.getHolderView().findViewById(R.id.txtFilterLocation);
                if (filter.getFromLOC() != null) {
                    txtView.setText(filter.getFromLOC().getAddress());
                } else {
                    txtView.setText(R.string.last_location_error);
                }
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private void doFilterByDate() {
        DateRangePickerDialog dateRangePicker = new DateRangePickerDialog();
        dateRangePicker.setCallback(this);
        dateRangePicker.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        dateRangePicker.show(getFragmentManager(), "DATE_RANGE_PICKER");
    }

    private void doFilterByLocation() {
        Intent intent = new Intent(MainActivity.this, LocationSearchActivity.class);
        startActivityForResult(intent, PERMISSION_LOCATION);
    }

    private void openSort() {
        sortDialog.show();
    }

    @Override
    public void onDateTimeRecurrenceSet(SelectedDate selectedDate, int hourOfDay, int minute, SublimeRecurrencePicker.RecurrenceOption recurrenceOption, String recurrenceRule) {
        Calendar start = selectedDate.getStartDate();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        long startDate = start.getTimeInMillis();

        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(System.currentTimeMillis());
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        long endDate = end.getTimeInMillis();


        viewModel.setFilterDate(startDate, endDate);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();

        if (activityMainBinding.contentMain.searchbar.getVisibility() == View.VISIBLE) {
            menuInflater.inflate(R.menu.menu_search, menu);
        } else {
            menuInflater.inflate(R.menu.menu_tool, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.actionSearch:
                openSearchBar();
                break;
            case R.id.actionQr:
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    runnable = () -> startActivity(new Intent(MainActivity.this, QRScanActivity.class).putExtra(AppConstants.ACTIVITY_BEHAVIOUR_TYPE, AppConstants.QR_SCAN_REQUEST));
                    addResultListener(PERMISSION_CAMERA, listener);
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_CAMERA);
                } else {
                    startActivity(new Intent(MainActivity.this, QRScanActivity.class).putExtra(AppConstants.ACTIVITY_BEHAVIOUR_TYPE, AppConstants.QR_SCAN_REQUEST));
                }
                break;
            case R.id.actionFilter:
                openSort();
                break;
            case android.R.id.home:
                if (activityMainBinding.contentMain.searchbar.getVisibility() == View.VISIBLE) {
                    setupActionBar();
                } else {
                    finish();
                }
                break;
        }

        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent result) {
        super.onActivityResult(reqCode, resCode, result);
        if (reqCode == PERMISSION_LOCATION && resCode == Activity.RESULT_OK) {
            int radius = result.getIntExtra(AppConstants.EXTRA_RADIUS, 0);


            viewModel.setFilterLocation(session.getLocation(), radius);
        }
    }

    @Override
    public void onPersonDetail(Person person) {
        Intent intent = new Intent(MainActivity.this, CreateDataActivity.class);
        intent.putExtra(AppConstants.EXTRA_QR, person.getQrcode());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MeasureNotification.dismissNotification(this);
        PersonRepository repository = PersonRepository.getInstance(this);
        if (repository.isUpdated()) {
            viewModel.setFilterOwn();
            viewModel.setSortType(AppConstants.SORT_DATE);
            repository.setUpdated(false);
        }
        SyncingWorkManager.startSyncingWithWorkManager(getApplicationContext());
        deviceCheckPopup();
        checkIfStdTestActive();
    }


    private final BaseActivity.ResultListener listener = new BaseActivity.ResultListener() {
        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            if (grantResults.length > 0) {
                runnable.run();
                runnable = null;
            }
        }
    };

    private void deviceCheckPopup() {
        long timestamp = LocalPersistency.getLong(this, DeviceCheckActivity.KEY_LAST_DEVICE_CHECK);
        if (System.currentTimeMillis() - timestamp > REQUEST_DEVICE_CHECK_TIME) {
            ConfirmDialog confirmDialog = new ConfirmDialog(this);
            confirmDialog.setMessage(getString(R.string.device_check_reminder));
            confirmDialog.setConfirmListener(result -> {
                if (result) {
                    startActivity(new Intent(MainActivity.this, DeviceCheckActivity.class));
                } else {
                    LocalPersistency.setString(this, KEY_LAST_DEVICE_CHECK_ISSUES, DeviceCheckFragment.IssueType.CHECK_REFUSED.toString());
                }
            });
            confirmDialog.show();
        }
    }

    private void showStdTestButtonInMenu(boolean visible) {
        activityMainBinding.navMenu.getMenu().findItem(R.id.menuQuitStdTest).setVisible(visible);
    }

    private void checkIfStdTestActive() {
        if (session.getStdTestQrCode() != null) {
            activityMainBinding.contentMain.toolbar.setBackgroundResource(R.color.colorPink);
            showStdTestButtonInMenu(true);
        } else {
            activityMainBinding.contentMain.toolbar.setBackgroundResource(R.color.colorPrimary);
            showStdTestButtonInMenu(false);
        }
    }

    private void showConfirmDialog(int message, int step) {
        try {
            ConfirmDialog confirmDialog = new ConfirmDialog(this);
            confirmDialog.setMessage(message);
            confirmDialog.setConfirmListener(result -> {
                if (result) {
                    session.setStdTestQrCode(null);
                    adapterData.notifyDataSetChanged();
                    checkIfStdTestActive();
                }
            });
            confirmDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
