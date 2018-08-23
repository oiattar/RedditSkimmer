package com.android.omar.redditskimmer;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.omar.redditskimmer.RedditContract.*;
import com.google.firebase.analytics.FirebaseAnalytics;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor>, SelectUserDialog.SelectUserDialogListener {

    private SubredditListAdapter mSubredditListAdapter;
    private PostsListAdapter mPostListAdapter;

    private FirebaseAnalytics mFirebaseAnalytics;

    @BindView(R.id.subreddit_list) ListView mSubredditListView;
    @BindView(R.id.posts_list) ListView mPostsListView;
    @BindView(R.id.search_box) EditText mSearchBox;
    @BindView(R.id.search_button) ImageButton mSearchButton;
    @BindView(R.id.swipe_refresh_layout_subreddit) SwipeRefreshLayout mSwipeRefreshLayoutSubreddit;
    @BindView(R.id.swipe_refresh_layout_posts) SwipeRefreshLayout mSwipeRefreshLayoutPosts;

    private static final int SUBREDDIT_LIST_LOADER = 0;
    private static final int POST_LIST_LOADER = 1;

    private static String mSearchString;
    private boolean mIsRefreshingSubreddits = false;
    private boolean mIsRefreshingPosts = false;

    private Boolean mLoaderLoggedInUserMode = null;
    private Boolean mLoaderSearchMode = null;

    //Data for list of subreddits on drawer
    static final int COL_SUBREDDIT_ROWID = 0;
    public static final int COL_SUBREDDIT_NAME = 1;
    public static final int COL_IS_SUBSCRIBER = 2;
    public static final String[] LOGGEDON_SUBREDDIT_LIST_COLUMNS = {
            SubredditEntry._ID,
            SubredditEntry.COLUMN_NAME,
            SubredditEntry.COLUMN_USER_IS_SUBSCRIBER
    };
    private static final String[] LOGGEDON_SUBREDDIT_SEARCH_LIST_COLUMNS = {
            SubredditSearchEntry._ID,
            SubredditSearchEntry.COLUMN_NAME,
            /*
            * (select count(*) from subreddits where subreddits.name = search.name and
            * subreddits.user_is_subscriber = 1) as user_is_subscriber
            * */
            "(select count(*) from " + SubredditEntry.TABLE_NAME + " where " +
                    SubredditEntry.TABLE_NAME + "." + SubredditEntry.COLUMN_NAME + "=" +
                    SubredditSearchEntry.TABLE_NAME + "." + SubredditSearchEntry.COLUMN_NAME +
                    " and  " + SubredditEntry.TABLE_NAME + "." +
                    SubredditEntry.COLUMN_USER_IS_SUBSCRIBER + "=1) as " +
                    SubredditEntry.COLUMN_USER_IS_SUBSCRIBER
    };
    private static final String[] ANON_SUBREDDIT_SEARCH_LIST_COLUMNS = {
            SubredditSearchEntry._ID,
            SubredditSearchEntry.COLUMN_NAME,
            /*
            * (select count(*) from subscr where subscr.name = search.name) as user_is_subscriber
            * */
            "(select count(*) from " + AnonSubscriberEntry.TABLE_NAME + " where " +
                    AnonSubscriberEntry.TABLE_NAME + "." + AnonSubscriberEntry.COLUMN_NAME + "=" +
                    SubredditSearchEntry.TABLE_NAME + "." + SubredditSearchEntry.COLUMN_NAME +
                    ") as " + SubredditEntry.COLUMN_USER_IS_SUBSCRIBER
    };
    public static final String SUBREDDITS_DEFAULT_SORT_ORDER =
            SubredditEntry.COLUMN_USER_IS_SUBSCRIBER + " desc";

    //Data for list of posts on main_activity layout
    //should match order of columns in RedditProvider.query(), under "CASE LINK"
    static final int COL_LINK_ROWID = 0;
    static final int COL_POSITION = 1;
    static final int COL_COUNT = 2;
    static final int COL_ID = 3;
    static final int COL_TITLE = 4;
    static final int COL_DOMAIN = 5;
    static final int COL_AUTHOR = 6;
    static final int COL_SCORE = 7;
    static final int COL_NUM_COMMENTS = 8;
    static final int COL_SUBREDDIT = 9;
    static final int COL_CREATED_UTC = 10;
    static final int COL_THUMBNAIL = 11;
    static final int COL_SAVED_POSITION = 12;
    static final int COL_MAX_SCORE = 13;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Constants.NEW_USER_EVENT)) {
                updateNavigationViewHeader();
                new RedditRestClient(MainActivity.this).beginRetrievingMySubreddits();
            } else if (action.equals(Constants.SUBREDDITS_RETRIEVED_EVENT)) {
                refreshPosts();
            } else if (action.equals(Constants.MY_SUBREDDITS_RETRIEVED_EVENT)) {
                new RedditRestClient(MainActivity.this).beginRetrievingSubreddits(
                        RedditRestClient.SubrdtDisplayOrder.DEFAULT);
            } else if (action.equals(Constants.POSTS_RETRIEVED_EVENT)) {

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ButterKnife.bind(this);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // register broadcast receiver for time user name retrieved from Reddit
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.NEW_USER_EVENT);
        filter.addAction(Constants.SUBREDDITS_RETRIEVED_EVENT);
        filter.addAction(Constants.MY_SUBREDDITS_RETRIEVED_EVENT);
        filter.addAction(Constants.POSTS_RETRIEVED_EVENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, filter);

        // set up cursor adapter for subreddit list
        mSubredditListAdapter = new SubredditListAdapter(this, null, 0);
        if (mSubredditListView != null)
            mSubredditListView.setAdapter(mSubredditListAdapter);
        getLoaderManager().initLoader(SUBREDDIT_LIST_LOADER, null, this);

        // set up cursor adapter for posts list
        mPostListAdapter = new PostsListAdapter(this, mPostsListView, null, 0);
        if (mPostsListView != null) {
            mPostsListView.setAdapter(mPostListAdapter);
            mPostsListView.setDividerHeight(4);
            mPostsListView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mPostListAdapter.getGestureDetector().onTouchEvent(event);
                    return mPostsListView.onTouchEvent(event);
                }
            });
        }
        getLoaderManager().initLoader(POST_LIST_LOADER, null, this);

        // update user name in drawer time log in
        updateNavigationViewHeader();

        // set up swipe refresh layouts
        if (mSwipeRefreshLayoutSubreddit != null ) {
            mSwipeRefreshLayoutSubreddit.setColorSchemeResources(R.color.colorAccentLight,
                    R.color.colorAccent, R.color.colorPrimary);
            mSwipeRefreshLayoutSubreddit.setOnRefreshListener(
                    new SwipeRefreshLayout.OnRefreshListener() {
                        @Override
                        public void onRefresh() {
                            refreshSubreddits();
                        }
                    });
        }

        if (mSwipeRefreshLayoutPosts != null ) {
            mSwipeRefreshLayoutPosts.setColorSchemeResources(R.color.colorAccentLight,
                    R.color.colorAccent, R.color.colorPrimary);
            mSwipeRefreshLayoutPosts.setOnRefreshListener(
                    new SwipeRefreshLayout.OnRefreshListener() {
                        @Override
                        public void onRefresh() {
                            refreshPosts();
                        }
                    });
        }

        // set up search box and button
        mSearchBox.setOnEditorActionListener(mOnEditorActionListener);

        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoaderSearchMode != null && mLoaderSearchMode) {
                    //Quit search results mode
                    setSubredditLoaderMode(false, null);
                    updateSearchUI(false);
                } else { //Start new search
                    mOnEditorActionListener.onEditorAction(mSearchBox,
                            EditorInfo.IME_ACTION_SEARCH, null);
                }
            }
        });

        // retrieve subreddit list if empty
        retrieveSubreddits();
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }

    /**
     * Listener responsible for closing onscreen keyboard and starting search once user clicked
     * on 'Start Search' button (on onscreen keyboard)
     */
    TextView.OnEditorActionListener mOnEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Hide virtual keyboard
                InputMethodManager imm =
                        (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(),
                        InputMethodManager.RESULT_UNCHANGED_SHOWN);

                mSearchString = v.getText().toString();
                beginSearchForSubreddit();
                handled = true;
            }
            return handled;
        }
    };

    private void updateSearchUI(boolean isSearchResultsMode) {
        if (isSearchResultsMode) {
            mSearchButton.setImageDrawable(ContextCompat.getDrawable(this,
                    R.drawable.ic_highlight_off_black_24dp));
            mSearchBox.selectAll();

        } else {
            mSearchButton.setImageDrawable(ContextCompat.getDrawable(this,
                    R.drawable.ic_search_black_24dp));
            mSearchBox.setSelection(0);
            mSearchString = null;
        }
    }

    public static String getSearchString() {
        return mSearchString;
    }

    /**
     * search for subreddits from search box
     */
    private void beginSearchForSubreddit() {
        if (mSearchString == null || mSearchString.length() == 0) {
            Toast.makeText(MainActivity.this, getString(R.string.empty_search_string),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        getContentResolver().delete(SubredditSearchEntry.CONTENT_URI, null, null);
        new RedditRestClient(this).beginSearchRedditNames(mSearchString,
                new RedditRestClient.ResultHandler() {
                    @Override
                    public void onSuccess() {
                        //Show toast with number of matches found
                        int numMatches = Util.getCount(MainActivity.this,
                                SubredditSearchEntry.CONTENT_URI, null, null);
                        Toast.makeText(MainActivity.this,
                                String.format(getString(R.string.search_success_message),
                                        numMatches), Toast.LENGTH_SHORT).show();
                        //Switch loader to show search results
                        setSubredditLoaderMode(true, null);
                        updateSearchUI(true);
                    }
                    @Override
                    public void onFailure(int errorCode) {
                        //Show toast that search failed
                        Toast.makeText(MainActivity.this,
                                String.format(getString(R.string.search_failure_message),
                                        errorCode), Toast.LENGTH_SHORT).show();
                        //Switch loader to show regular list of subreddits
                        setSubredditLoaderMode(false, null);
                        updateSearchUI(false);
                    }
                });
    }

    private void setSubredditLoaderMode(Boolean isSearchResultsMode, Boolean isLoggedInUserMode) {
        boolean isRestartRequired = false;
        if (isSearchResultsMode != null && mLoaderSearchMode != isSearchResultsMode) {
            mLoaderSearchMode = isSearchResultsMode;
            isRestartRequired = true;
        }
        if (isLoggedInUserMode != null && (mLoaderLoggedInUserMode == null ||
                mLoaderLoggedInUserMode != isLoggedInUserMode)) {
            mLoaderLoggedInUserMode = isLoggedInUserMode;
            isRestartRequired = true;
        }
        if (isRestartRequired) {
            getLoaderManager().restartLoader(SUBREDDIT_LIST_LOADER, null, MainActivity.this);
        }
    }

    private void refreshPosts() {
        if (mIsRefreshingPosts)
            return;
        mIsRefreshingPosts = true;
        togglePostsRefreshIndicator();

        Util.deleteLinksFromDb(this);
        Util.deleteCurrLinksFromDb(this);

        ContentResolver cr = getContentResolver();
        Cursor cur;
        if (Util.isLoggedIn(this)) {
            cur = cr.query(SubredditEntry.CONTENT_URI, new String[] {SubredditEntry.COLUMN_NAME},
                    SubredditEntry.COLUMN_USER_IS_SUBSCRIBER + "=?", new String[] {"1"}, null);
        } else {
            cur = cr.query(AnonSubscriberEntry.CONTENT_URI,
                    new String[] {AnonSubscriberEntry.COLUMN_NAME}, null, null, null);
        }

        if (cur == null || cur.getCount() == 0 || !cur.moveToFirst()) {
            mIsRefreshingPosts = false;
            togglePostsRefreshIndicator();
            if (cur != null)
                cur.close();
        } else {
            RedditRestClient rrc = new RedditRestClient(this);
            do {
                String subreddit = cur.getString(0);
                rrc.beginRetrievingLinks(subreddit, RedditRestClient.LinkDisplayOrder.TOP);
                //break;
            } while (cur.moveToNext());
        }
        if (cur != null && !cur.isClosed())
            cur.close();
    }

    private void refreshSubreddits() {
        if (mIsRefreshingSubreddits)
            return;
        mIsRefreshingSubreddits = true;
        toggleSubredditRefreshIndicator();

        if (mLoaderSearchMode != null && mLoaderSearchMode) {
            beginSearchForSubreddit();
        } else {
            Util.deleteSubredditsFromDb(this);
            if (Util.isLoggedIn(this)) {
                new RedditRestClient(this).beginRetrievingMySubreddits();
            } else { //Anonymous
                new RedditRestClient(this).beginRetrievingSubreddits(
                        RedditRestClient.SubrdtDisplayOrder.DEFAULT);
            }
        }
    }

    private void retrieveSubreddits() {
        //Retrieve list of subreddits time list is empty
        Util.getCount(this, SubredditEntry.CONTENT_URI, null, null);
        if (0 >= Util.getCount(this, SubredditEntry.CONTENT_URI, null, null)) {
            refreshSubreddits();
        }
    }

    /**
     * Toggle swipe refresh loading indicator
     */
    private void toggleSubredditRefreshIndicator() {
        mSwipeRefreshLayoutSubreddit.setRefreshing(mIsRefreshingSubreddits);
    }

    private void togglePostsRefreshIndicator() {
        mSwipeRefreshLayoutPosts.setRefreshing(mIsRefreshingPosts);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        if (i == SUBREDDIT_LIST_LOADER) {
            String[] projection = null;
            String sortOrder = null;
            Uri uri = null;

            boolean isLoggedIn = Util.isLoggedIn(this);
            if (mLoaderLoggedInUserMode != null)
                isLoggedIn = mLoaderLoggedInUserMode;
            boolean isSearchResults = false;
            if (mLoaderSearchMode != null)
                isSearchResults = mLoaderSearchMode;

            if (isLoggedIn) {
                projection = LOGGEDON_SUBREDDIT_LIST_COLUMNS;
                sortOrder = SUBREDDITS_DEFAULT_SORT_ORDER;
                uri = SubredditEntry.CONTENT_URI;
            } else {
                uri = SubredditEntry.buildUriWithSubpath(RedditContract.SUBPATH_ANONYMOUS);
            }

            if (isSearchResults) {
                uri = SubredditSearchEntry.CONTENT_URI;
                sortOrder = null;
                if (isLoggedIn) {
                    projection = LOGGEDON_SUBREDDIT_SEARCH_LIST_COLUMNS;
                } else {
                    projection = ANON_SUBREDDIT_SEARCH_LIST_COLUMNS;
                }
            }
            return new CursorLoader(this, uri, projection, null /*String selection*/,
                    null /*String[] selectionArgs*/, sortOrder);
        } else if (i == POST_LIST_LOADER) {
            return new CursorLoader(this,
                    LinkEntry.buildUriWithSubpath(RedditContract.SUB_PATH_LINKS_TOP_N), null,
                    null /*String selection*/,
                    null /*String[] selectionArgs*/, null);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        int id = loader.getId();
        int mSubredditListPos = ListView.INVALID_POSITION;
        if (id == SUBREDDIT_LIST_LOADER) {
            mSubredditListAdapter.swapCursor(cursor);
            if (mSubredditListPos != ListView.INVALID_POSITION) {
                mSubredditListView.smoothScrollToPosition(mSubredditListPos);
            }
            //Hide looping-circle indicator
            mIsRefreshingSubreddits = false;
            toggleSubredditRefreshIndicator();
        } else if (id == POST_LIST_LOADER) {
            mPostListAdapter.swapCursor(cursor);
            int mPostListPos = ListView.INVALID_POSITION;
            if (mPostListPos != ListView.INVALID_POSITION) {
                mPostsListView.smoothScrollToPosition(mSubredditListPos);
            }
            mIsRefreshingPosts = false;
            togglePostsRefreshIndicator();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        int id = loader.getId();
        if (id == SUBREDDIT_LIST_LOADER) {
            mSubredditListAdapter.swapCursor(null);
            mIsRefreshingSubreddits = false;
            toggleSubredditRefreshIndicator();
        } else if (id == POST_LIST_LOADER) {
            mPostListAdapter.swapCursor(null);
            mIsRefreshingPosts = false;
            togglePostsRefreshIndicator();
        }
    }

    private void updateNavigationViewHeader() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        TextView usernameView = navigationView.findViewById(R.id.username);
        String username = Util.getUserName(this);
        if (username == null) {
            usernameView.setText(R.string.nav_header_title);
        } else {
            usernameView.setText(username);
        }
    }

    public void onLogInPressed(View v) {
        DialogFragment dialog = new SelectUserDialog();
        dialog.show(getSupportFragmentManager(), "SelectUserDialog");
    }

    /**
     * Handle click on 'OK' button on 'Select User' dialog
     * @param dialog
     */
    public void onDialogPositiveClick(DialogFragment dialog) {
        SelectUserDialog.Result res = ((SelectUserDialog)dialog).getResult();
        if (res == SelectUserDialog.Result.NoChanges)
            return;

        if (res == SelectUserDialog.Result.NewUser) {
            Intent intent = new Intent(this, RedditAuthActivity.class);
            startActivityForResult(intent, RedditAuthActivity.REQUEST_CODE_AUTHORIZATION);
            return;
        }

        updateNavigationViewHeader();
        updateSearchUI(false);
        setSubredditLoaderMode(false,res == SelectUserDialog.Result.UserChangedToLoggedOn);
        Util.deleteSubredditsFromDb(this);

        if (res == SelectUserDialog.Result.UserChangedToLoggedOn) {
            new RedditRestClient(this).beginRetrievingUserName(null);
        } else { //UserChangedToAnonymous
            new RedditRestClient(this).beginRetrievingSubreddits(
                    RedditRestClient.SubrdtDisplayOrder.DEFAULT);
        }
    }

    /**
     * Handles results from Authorization activity
     * @param requestCode
     * @param resultCode
     * @param data
     */
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == RedditAuthActivity.REQUEST_CODE_AUTHORIZATION) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(MainActivity.this, getString(R.string.auth_failed),
                        Toast.LENGTH_LONG).show();
            } else {
                updateNavigationViewHeader();
                updateSearchUI(false);
                setSubredditLoaderMode(false, true);
                Util.deleteSubredditsFromDb(this);
            }
        }
    }

    /**
     * Handle click on 'Cancel' button on 'Select User' dialog
     * @param dialog
     */
    public void onDialogNegativeClick(DialogFragment dialog) {
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
