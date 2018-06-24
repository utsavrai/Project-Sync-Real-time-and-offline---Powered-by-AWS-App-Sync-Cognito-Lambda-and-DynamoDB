package com.rai.utsav;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.amazonaws.demo.appsync.ListProjectsQuery;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.rai.utsav.R;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public class ListProjectsActivity extends AppCompatActivity {
    private NavigationView nDrawer;
    private DrawerLayout mDrawer;
    private ActionBarDrawerToggle mDrawerToggle;
    private Toolbar toolbar;
    private AlertDialog userDialog;
    private ProgressDialog waitDialog;
    private ListView attributesList;

    // Cognito user objects
    private CognitoUser user;
    private CognitoUserSession session;
    private CognitoUserDetails details;

    // User details
    private String username;
    private static final String TAG = ListProjectsActivity.class.getSimpleName();

    private AWSAppSyncClient mAWSAppSyncClient;

    private List<ListProjectsQuery.Item> projects = new ArrayList<>();
    private ListView mListView;
    private ProjectsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects);
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
        //toolbar.setNavigationIcon(R.mipmap.icons8_desura);

        toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        toolbar.setTitle("");
        TextView main_title = (TextView) findViewById(R.id.main_toolbar_title);
        main_title.setText("Projects");
        setSupportActionBar(toolbar);
        mDrawer = (DrawerLayout) findViewById(R.id.user_drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this,mDrawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        mDrawer.addDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        nDrawer = (NavigationView) findViewById(R.id.nav_view);
        setNavDrawer();
        init();
        View navigationHeader = nDrawer.getHeaderView(0);
        TextView navHeaderSubTitle = (TextView) navigationHeader.findViewById(R.id.textViewNavUserSub);
        navHeaderSubTitle.setText(username);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ListProjectsActivity.this, AddProjectActivity.class);
                startActivity(intent);
            }
        });

        FloatingActionButton fabRefresh = (FloatingActionButton) findViewById(R.id.refresh);
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                query();
            }
        });
            adapter = new ProjectsAdapter(this, projects);
            mListView = (ListView) findViewById(R.id.postsList);
            mListView.setAdapter(adapter);

    }

    @Override
    protected void onResume() {
        super.onResume();
        query();
    }

    private void query() {
        if (mAWSAppSyncClient == null) {
            mAWSAppSyncClient = ClientFactory.getInstance(this);
        }
        mAWSAppSyncClient.query(ListProjectsQuery.builder().build())
                .responseFetcher(AppSyncResponseFetchers.CACHE_AND_NETWORK)
                .enqueue(projectsCallback);
    }

    private GraphQLCall.Callback<ListProjectsQuery.Data> projectsCallback = new GraphQLCall.Callback<ListProjectsQuery.Data>() {
        @Override
        public void onResponse(@Nonnull Response<ListProjectsQuery.Data> response) {
            projects = response.data().listProjects().items();
            if(projects !=null){
            adapter.setProjects(projects);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Notifying data set changed");
                    adapter.notifyDataSetChanged();
                }
            });}
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e(TAG, "Failed to make projects api call", e);
            Log.e(TAG, e.getMessage());
        }
    };

    private void setNavDrawer() {
        nDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                performAction(item);
                return true;
            }
        });
    }

    // Perform the action for the selected navigation item
    private void performAction(MenuItem item) {
        // Close the navigation drawer
        mDrawer.closeDrawers();

        // Find which item was selected
        switch(item.getItemId()) {

            case R.id.nav_user_change_password:
                // Change password
                changePassword();
                break;

            case R.id.nav_user_sign_out:
                // Sign out from this account
                signOut();
                break;

            case R.id.nav_user_about:
                // For the inquisitive
                Intent aboutAppActivity = new Intent(this, AboutApp.class);
                startActivity(aboutAppActivity);
                break;
        }
    }

    // Change user password
    private void changePassword() {
        Intent changePssActivity = new Intent(this, ChangePasswordActivity.class);
        startActivity(changePssActivity);
    }

    // Sign out user
    private void signOut() {
        user.signOut();
        exit();
    }

    // Initialize this activity
    private void init() {
        // Get the user name
        Bundle extras = getIntent().getExtras();
        username = AppHelper.getCurrUser();
        user = AppHelper.getPool().getUser(username);

    }

    private void exit () {
        Intent intent = new Intent();
        if(username == null)
            username = "";
        intent.putExtra("name",username);
        setResult(RESULT_OK, intent);
        finish();
    }

}
