package com.android.omar.redditskimmer;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

public class SkimmerWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new SkimmerRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class SkimmerRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    public static final int COL_ROWID = 0;
    public static final int COL_POSITION = 1;
    public static final int COL_NUM_LINKS = 2;
    public static final int COL_TITLE = 3;
    public static final int COL_SCORE = 4;
    public static final int COL_NUM_CMNTS = 5;
    public static final int COL_SUBREDDIT = 6;

    private Context mContext;
    private int mAppWidgetId;
    Cursor mCursor;

    public SkimmerRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
        mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onDataSetChanged() {
        if (mCursor != null)
            mCursor.close();

        Uri uri = RedditContract.LinkEntry.buildUriWithSubpath(RedditContract.SUB_PATH_LINKS_TOP_N_WIDGET);
        mCursor = mContext.getContentResolver().query(uri, null, null, null, " ASC LIMIT 5");

        boolean curNull = mCursor == null;
    }

    @Override
    public void onDestroy() {
        if (mCursor != null)
            mCursor.close();
    }

    @Override
    public int getCount() {
        return mCursor != null ? mCursor.getCount() : 0;
    }

    @Override
    public RemoteViews getViewAt(int i) {
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.widget_list_item);

        if (mCursor != null && mCursor.getCount() > 0 && mCursor.moveToPosition(i)) {

            mCursor.moveToPosition(i);
            rv.setTextViewText(R.id.widget_post_subreddit, mCursor.getString(COL_SUBREDDIT));
            rv.setContentDescription(R.id.widget_post_subreddit, mCursor.getString(COL_SUBREDDIT));
            rv.setTextViewText(R.id.widget_post_title, mCursor.getString(COL_TITLE));
            rv.setContentDescription(R.id.widget_post_title, mCursor.getString(COL_TITLE));

            // intent to open post
            Intent intent = new Intent();
            intent.putExtra(Constants.EXTRA_SUBREDDIT_NAME, mCursor.getString(COL_SUBREDDIT));
            intent.putExtra(Constants.EXTRA_LINK_COUNT, mCursor.getInt(COL_NUM_LINKS));
            intent.putExtra(Constants.EXTRA_LINK_POSITION, mCursor.getInt(COL_POSITION));
            rv.setOnClickFillInIntent(R.id.widget_item, intent);
        }
        return rv;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }
}
