package com.rai.utsav;
import android.app.ProgressDialog;
import android.content.Context;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.demo.appsync.CommentOnProjectMutation;
import com.amazonaws.demo.appsync.DeleteProjectMutation;
import com.amazonaws.demo.appsync.GetProjectQuery;
import com.amazonaws.demo.appsync.NewCommentOnProjectSubscription;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.rai.utsav.R;
import com.amazonaws.demo.appsync.fragment.Project;
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;

public class ViewProjectActivity extends AppCompatActivity {
    public static final String TAG = ViewProjectActivity.class.getSimpleName();
    private static Project project;
    private TextView name, time, where, description, comments;
    private EditText newComment;
    private AppSyncSubscriptionCall<NewCommentOnProjectSubscription.Data> subscriptionWatcher;

    public static void startActivity(final Context context, Project p) {
        project = p;
        Intent intent = new Intent(context, ViewProjectActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_project);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.mipmap.icons8_desura);
        name = (TextView) findViewById(R.id.viewName);
        time = (TextView) findViewById(R.id.viewTime);
        description = (TextView) findViewById(R.id.viewDescription);
        comments = (TextView) findViewById(R.id.comments);
        newComment = (EditText) findViewById(R.id.new_comment);

        name.setText(project.name());
        time.setText(project.when());
        description.setText(project.description());


        refreshProject(true);
        startSubscription();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fabd);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Intent intent = new Intent(ListProjectsActivity.this, AddProjectActivity.class);
                //startActivity(intent);
                DeleteProjectMutation delete = DeleteProjectMutation.builder().id(project.id().toString())
                        .build();

                ClientFactory.getInstance(view.getContext())
                        .mutate(delete)
                        .enqueue(addDeleteCallback);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (subscriptionWatcher != null) {
            subscriptionWatcher.cancel();
        }
    }


    private void startSubscription() {
        NewCommentOnProjectSubscription subscription = NewCommentOnProjectSubscription.builder().projectId(project.id()).build();

        subscriptionWatcher = ClientFactory.getInstance(this.getApplicationContext()).subscribe(subscription);
        subscriptionWatcher.execute(subscriptionCallback);
    }

    private AppSyncSubscriptionCall.Callback<NewCommentOnProjectSubscription.Data> subscriptionCallback = new AppSyncSubscriptionCall.Callback<NewCommentOnProjectSubscription.Data>() {
        @Override
        public void onResponse(final @Nonnull Response<NewCommentOnProjectSubscription.Data> response) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ViewProjectActivity.this, response.data().subscribeToProjectComments().projectId().substring(0, 5) + response.data().subscribeToProjectComments().content(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Subscription response: " + response.data().toString());
                    NewCommentOnProjectSubscription.SubscribeToProjectComments comment = response.data().subscribeToProjectComments();

                    // UI only write
                    addComment(comment.content());

                    // Cache write
                    addCommentToCache(comment);

                    // Show changes from in cache
                    refreshProject(true);
                }
            });
        }

        @Override
        public void onFailure(final @Nonnull ApolloException e) {
            Log.e(TAG, "Subscription failure", e);
        }

        @Override
        public void onCompleted() {
            Log.d(TAG, "Subscription completed");
        }
    };

    /**
     * Adds the new comment to the project in the cache.
     * @param comment
     */
    private void addCommentToCache(NewCommentOnProjectSubscription.SubscribeToProjectComments comment) {
        try {
            // Read the old project data
            GetProjectQuery getProjectQuery = GetProjectQuery.builder().id(project.id()).build();
            GetProjectQuery.Data readData = ClientFactory.getInstance(ViewProjectActivity.this).getStore().read(getProjectQuery).execute();
            Project project = readData.getProject().fragments().project();

            // Create the new comment object
            Project.Item newComment = new Project.Item(
                    comment.__typename(),
                    comment.projectId(),
                    comment.commentId(),
                    comment.content(),
                    comment.createdAt());

            // Create the new comment list attached to the project
            List<Project.Item> items = new LinkedList<>(project.comments().items());
            items.add(0, newComment);

            // Create the new project data
            GetProjectQuery.Data madeData = new GetProjectQuery.Data(new GetProjectQuery.GetProject(readData.getProject().__typename(), new GetProjectQuery.GetProject.Fragments(new Project(readData.getProject().fragments().project().__typename(),
                    project.id(),
                    project.description(),
                    project.name(),
                    project.when(),
                    new Project.Comments(readData.getProject().fragments().project().comments().__typename(), items)))));

            // Write the new project data
            ClientFactory.getInstance(ViewProjectActivity.this).getStore().write(getProjectQuery, madeData).execute();
            Log.d(TAG, "Wrote comment to database");
        } catch (ApolloException e) {
            Log.e(TAG, "Failed to update local database", e);
        }
    }

    /**
     * UI triggered method to add a comment. This will read the text box and submit a new comment.
     * @param view
     */
    public void addComment(View view) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(newComment.getWindowToken(), 0);

        Toast.makeText(this, "Submitting comment", Toast.LENGTH_SHORT).show();

        CommentOnProjectMutation comment = CommentOnProjectMutation.builder().content(newComment.getText().toString())
                .createdAt(new Date().toString())
                .projectId(project.id())
                .build();

        ClientFactory.getInstance(view.getContext())
                .mutate(comment)
                .enqueue(addCommentCallback);
    }

    /**
     * Service response subscriptionCallback confirming receipt of new comment triggered by UI.
     */
    private GraphQLCall.Callback<CommentOnProjectMutation.Data> addCommentCallback = new GraphQLCall.Callback<CommentOnProjectMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<CommentOnProjectMutation.Data> response) {
            Log.d(TAG, response.toString());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    clearComment();
                }
            });
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e(TAG, "Failed to make comments mutation", e);
            Log.e(TAG, e.getMessage());
        }
    };

    /**
     * Refresh the project object to latest from service.
     */
    private void refreshProject(final boolean cacheOnly) {
        GetProjectQuery getProjectQuery = GetProjectQuery.builder().id(project.id()).build();

        ClientFactory.getInstance(getApplicationContext())
                .query(getProjectQuery)
                .responseFetcher(cacheOnly ? AppSyncResponseFetchers.CACHE_ONLY : AppSyncResponseFetchers.CACHE_AND_NETWORK)
                .enqueue(refreshProjectCallback);
    }

    private GraphQLCall.Callback<GetProjectQuery.Data> refreshProjectCallback = new GraphQLCall.Callback<GetProjectQuery.Data>() {
        @Override
        public void onResponse(@Nonnull final Response<GetProjectQuery.Data> response) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (response.errors().size() < 1) {
                        project = response.data().getProject().fragments().project();
                        refreshComments();
                    } else {
                        Log.e(TAG, "Failed to get project.");
                    }
                }
            });
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e(TAG, "Failed to get project.");
        }
    };

    /**
     * Triggered by subscriptions/programmatically
     * @param comment
     */
    private void addComment(final String comment) {
        comments.setText(comment + "\n-----------\n");
    }

    /**
     * Reads the comments from the project object and preps it for display.
     */
    private void refreshComments() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Project.Item i : project.comments().items()) {
            stringBuilder.append(i.content() + "\n---------\n");
        }
        comments.setText(stringBuilder.toString());
    }
    private GraphQLCall.Callback<DeleteProjectMutation.Data> addDeleteCallback = new GraphQLCall.Callback<DeleteProjectMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<DeleteProjectMutation.Data> response) {
            Log.d(TAG, response.toString());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent k = new Intent(ViewProjectActivity.this, ListProjectsActivity.class);
                        startActivity(k);
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e(TAG, "Failed to make comments mutation", e);
            Log.e(TAG, e.getMessage());
        }
    };
    private void clearComment() {
        newComment.setText("");
    }
}
