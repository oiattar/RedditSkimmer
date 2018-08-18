package com.android.omar.redditskimmer;

import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import butterknife.BindView;
import butterknife.ButterKnife;

public class PostWithCommentsActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = PostWithCommentsActivity.class.getSimpleName();

    static final int COL_TITLE = 3;
    static final int COL_URL = 15;

    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.container) ViewPager mViewPager;

    private SectionsPagerAdapter mSectionsPagerAdapter;

    private String mSubreddit;
    private int mLinkCount;
    private int mLinkPosition;
    private Cursor mCursorLinks;
    private ShareActionProvider mShareActionProvider;

    private static final int POSTS_PAGER_LOADER = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_with_comments);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        if (intent != null && intent.getAction().equals(Constants.ACTION_SHOW_LINKS_FOR_SUBREDDIT)) {
            mSubreddit = intent.getStringExtra(Constants.EXTRA_SUBREDDIT_NAME);
            mLinkCount = intent.getIntExtra(Constants.EXTRA_LINK_COUNT, 0);
            mLinkPosition = intent.getIntExtra(Constants.EXTRA_LINK_POSITION, 0);
        }

        getLoaderManager().initLoader(POSTS_PAGER_LOADER, null, this);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        if (mViewPager != null) {
            mViewPager.setAdapter(mSectionsPagerAdapter);
            mViewPager.setCurrentItem(mLinkPosition);
            mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    String url = getLinkUrl();
                    if (url != null) {
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, url);
                        sendIntent.setType("text/plain");
                        setShareIntent(sendIntent);
                    }
                }
                @Override
                public void onPageSelected(int position) {
                }
                @Override
                public void onPageScrollStateChanged(int state) {
                }
            });
        }
    }

    public void onBackPressed() {
        int position = mViewPager.getCurrentItem();
        if (position >= 0 && position < mLinkCount) {
            Util.updateSubredditPositionInDb(PostWithCommentsActivity.this, mSubreddit, position);
        }
        super.onBackPressed();
    }

    private String getLinkUrl() {
        String res = null;
        if (mCursorLinks == null || mCursorLinks.isClosed() || !mCursorLinks.moveToPosition(mViewPager.getCurrentItem()))
            return res;
        String url = mCursorLinks.getString(COL_URL);
        if (!url.startsWith(Constants.HTTP_PREFIX))
            return null;
        return mCursorLinks.getString(COL_URL);
    }

    public void onLinkClick (View view) {
        if (mCursorLinks == null || mCursorLinks.isClosed() ||
                !mCursorLinks.moveToPosition(mViewPager.getCurrentItem()))
            return;
        String url = mCursorLinks.getString(COL_URL);
        if (!url.startsWith(Constants.HTTP_PREFIX))
            return;
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(Constants.EXTRA_LINK_TITLE, mCursorLinks.getString(COL_TITLE));
        intent.putExtra(Constants.EXTRA_LINK_URL, mCursorLinks.getString(COL_URL));
        startActivity(intent);
    }

    private void refreshComments() {
        mSectionsPagerAdapter.notifyDataSetChanged();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        if (i == POSTS_PAGER_LOADER) {
            return new CursorLoader(this,
                    RedditContract.LinkEntry.buildUriWithSubpath(mSubreddit),
                    null /*String[] projection*/,
                    null /*String selection*/,
                    null /*String[] selectionArgs*/,
                    null /*sort order*/);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == POSTS_PAGER_LOADER) {
            mCursorLinks = data;
            if (mViewPager != null) {
                mSectionsPagerAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (loader.getId() == POSTS_PAGER_LOADER) {
            mCursorLinks = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_post_with_comments, menu);
        // Locate MenuItem with ShareActionProvider
        MenuItem item = menu.findItem(R.id.action_share_link);
        // Fetch and store ShareActionProvider
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
        return true;
    }

    private void setShareIntent(Intent shareIntent) {
        if (mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(shareIntent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_refresh_comments) {
            refreshComments();
        }
        return super.onOptionsItemSelected(item);
    }


    public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }
        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position, getIntent().getExtras(), mCursorLinks,
                    PostWithCommentsActivity.this);
        }
        @Override
        public int getCount() {
            return mLinkCount;
        }
        @Override
        public CharSequence getPageTitle(int position) {
            return null;
        }
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }
    }
}
