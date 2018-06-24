package com.rai.utsav;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.amazonaws.demo.appsync.AddProjectMutation;
import com.amazonaws.demo.appsync.ListProjectsQuery;
import com.rai.utsav.R;
import com.amazonaws.demo.appsync.fragment.Project;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

public class AddProjectActivity extends AppCompatActivity {

    private static final String TAG = AddProjectActivity.class.getSimpleName();

    private EditText name;
    private EditText time;
    private EditText description;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_project);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.mipmap.icons8_desura);
        name = (EditText)findViewById(R.id.name);
        time = (EditText)findViewById(R.id.time);
        description = (EditText)findViewById(R.id.description);

        name.setText("Project Name");

        time.setText("12:00 pm");
        description.setText("Testing");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_add_projects, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_save) {
            save();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void save() {
        String nameString = name.getText().toString();
        String timeString = time.getText().toString();
        String descriptionString = description.getText().toString();

        // Get the client instance
        AWSAppSyncClient awsAppSyncClient = ClientFactory.getInstance(this.getApplicationContext());

        // Create the mutation request
        AddProjectMutation addProjectMutation = AddProjectMutation.builder()
                .name(nameString)
                .when(timeString)
                .description(descriptionString)
                .build();

        // Enqueue the request (This will execute the request)
        awsAppSyncClient.mutate(addProjectMutation).refetchQueries(ListProjectsQuery.builder().build()).enqueue(addProjectsCallback);

        // Add to Project list while offline or before request returns
        List<Project.Item> items = new ArrayList<>();
        String tempID = UUID.randomUUID().toString();
        Project project = new Project("Project", tempID, descriptionString, nameString, timeString, new Project.Comments("Comment", items));
        addToListProjectsQuery(new ListProjectsQuery.Item("Project", new ListProjectsQuery.Item.Fragments(project)));

        // Close the add Project when offline otherwise allow callback to close
        ConnectivityManager cm =
                (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            finish();
        }
    }

    private GraphQLCall.Callback<AddProjectMutation.Data> addProjectsCallback = new GraphQLCall.Callback<AddProjectMutation.Data>() {
        @Override
        public void onResponse(@Nonnull final Response<AddProjectMutation.Data> response) {
            if (response.hasErrors()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Could not save", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error: " + response.toString());

                        for (Error err : response.errors()) {
                            Log.e(TAG, "Error: " + err.message());
                        }
                        finish();
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Saved", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Add Project succeeded");
                        finish();
                    }
                });
            }
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e(TAG, "Failed to make posts api call", e);
            Log.e(TAG, e.getMessage());
        }
    };

    private void addToListProjectsQuery(final ListProjectsQuery.Item pendingItem) {
        final AWSAppSyncClient awsAppSyncClient = ClientFactory.getInstance(this);
        final ListProjectsQuery listProjectsQuery = ListProjectsQuery.builder().build();

        awsAppSyncClient.query(listProjectsQuery)
                .responseFetcher(AppSyncResponseFetchers.CACHE_ONLY)
                .enqueue(new GraphQLCall.Callback<ListProjectsQuery.Data>() {
                    @Override
                    public void onResponse(@Nonnull Response<ListProjectsQuery.Data> response) {
                        List<ListProjectsQuery.Item> items = new ArrayList<>();
                        items.addAll(response.data().listProjects().items());
                        items.add(pendingItem);
                        ListProjectsQuery.Data data = new ListProjectsQuery.Data(new ListProjectsQuery.ListProjects("ProjectConnection", items));
                        awsAppSyncClient.getStore().write(listProjectsQuery, data).enqueue(null);
                    }

                    @Override
                    public void onFailure(@Nonnull ApolloException e) {
                        Log.e(TAG, "Failed to update Project query list.", e);
                    }
                });
    }
}
