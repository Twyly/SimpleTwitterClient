package com.codepath.apps.restclienttemplate.activities;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.codepath.apps.restclienttemplate.ErrorHelper;
import com.codepath.apps.restclienttemplate.adapters.TweetsArrayAdapter;
import com.codepath.apps.restclienttemplate.fragments.ComposeTweetDialog;
import com.codepath.apps.restclienttemplate.EndlessTweetScrollListener;
import com.codepath.apps.restclienttemplate.R;
import com.codepath.apps.restclienttemplate.TwitterApplication;
import com.codepath.apps.restclienttemplate.networking.TwitterClient;
import com.codepath.apps.restclienttemplate.models.Tweet;
import com.codepath.apps.restclienttemplate.models.User;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;


public class TimelineActivity extends ActionBarActivity implements ComposeTweetDialog.ComposteTweetDialogListener {

    private TwitterClient client;
    private TweetsArrayAdapter aTweets;
    private ArrayList<Tweet> tweets;
    private ListView lvTweets;
    private SwipeRefreshLayout swipeContainer;
    private User currentUser;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timeline);
        setupViewsAndAdapter();
        loadCachedTweets();
        client = TwitterApplication.getRestClient();
        fetchTweets(0);
    }

    private void setupViewsAndAdapter() {

        // Add twitter bird
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setLogo(R.drawable.ic_bird);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        getSupportActionBar().setElevation(0);


        swipeContainer = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        lvTweets = (ListView) findViewById(R.id.lvTweets);
        tweets = new ArrayList<Tweet>();
        aTweets = new TweetsArrayAdapter(this, tweets, lvTweets);
        lvTweets.setAdapter(aTweets);

        lvTweets.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Tweet tweet = tweets.get(position);
                showDetialActivity(tweet);
            }
        });

        lvTweets.setOnScrollListener(new EndlessTweetScrollListener(10) {
            @Override
            public void onLoadMore(long maxID, int totalItemsCount) {
                fetchTweets(maxID);
            }
        });

        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                fetchTweets(0);
            }
        });

        swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_timeline, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.miCompose) {
            showComposeDialog(null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    public void showComposeDialog(final Tweet tweet) {

        if (currentUser == null) {

            client.getCurrentUser(new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                    currentUser = User.fromJSON(response);
                    showComposeDialog(tweet);
                }
                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    ErrorHelper.showErrorAlert(TimelineActivity.this, ErrorHelper.ErrorType.GENERIC);
                }
            });
        } else {
            FragmentManager fm = getSupportFragmentManager();
            ComposeTweetDialog composeDialog;
            if (tweet != null) {
                composeDialog = ComposeTweetDialog.newInstance(currentUser, tweet);
            } else {
                composeDialog = ComposeTweetDialog.newInstance(currentUser);
            }
            composeDialog.show(fm, "fragment_compose_tweet");
        }
    }

    private void showDetialActivity(Tweet tweet) {
        Intent intent = new Intent(TimelineActivity.this, DetailTweetActivity.class);
        intent.putExtra("tweet", tweet);
        intent.putExtra("user", currentUser);
        startActivity(intent);
    }

    // Networking
    private void fetchTweets(final long maxID) {

        if (!client.isNetworkAvailable()) {
            ErrorHelper.showErrorAlert(this, ErrorHelper.ErrorType.NETWORK);
            swipeContainer.setRefreshing(false);
            return;
        }

        if (currentUser == null) {
            client.getCurrentUser(new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                    currentUser = User.fromJSON(response);
                    fetchTweets(maxID);
                }
                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    ErrorHelper.showErrorAlert(TimelineActivity.this, ErrorHelper.ErrorType.GENERIC);
                    swipeContainer.setRefreshing(false);
                }
            });
        } else {
            client.getHomeTimeline(maxID, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONArray json) {
                    Log.d("DEBUG", json.toString());
                    if (maxID <= 0) {
                        deleteCachedTweetsAndUsers();
                        aTweets.clear();
                    }
                    ArrayList<Tweet> newTweets = Tweet.saveFromJSONArray(json);

                    if (!newTweets.isEmpty()) {
                        aTweets.addAll(newTweets);
                    }
                    swipeContainer.setRefreshing(false);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    ErrorHelper.showErrorAlert(TimelineActivity.this, ErrorHelper.ErrorType.GENERIC);
                    swipeContainer.setRefreshing(false);
                }

            });
        }
    }

    private void loadCachedTweets() {
        List<Tweet> cachedTweets = new Select().from(Tweet.class)
                .orderBy("timestamp DESC").limit(100).execute();
        tweets.addAll(cachedTweets);
        aTweets.addAll(tweets);
    }

    private void deleteCachedTweetsAndUsers() {
        new Delete().from(Tweet.class).execute();
        new Delete().from(User.class).execute();
    }

    // ComposeTweetDialogListener
    @Override
    public void onFinishComposeTweet(Tweet tweet) {
        tweet.save();
        aTweets.insert(tweet, 0);
    }

}
