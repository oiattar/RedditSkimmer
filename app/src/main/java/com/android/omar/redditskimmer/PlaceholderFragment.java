package com.android.omar.redditskimmer;

import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * A placeholder fragment containing a simple view.
 */
public class PlaceholderFragment extends Fragment {

    public static final String TAG = PlaceholderFragment.class.getSimpleName();

    static final int COL_LINK_ROWID = 0;
    static final int COL_POSITION = 1;
    static final int COL_ID = 2;
    static final int COL_TITLE = 3;
    static final int COL_DOMAIN = 4;
    static final int COL_AUTHOR = 5;
    static final int COL_SCORE = 6;
    static final int COL_NUM_COMMENTS = 7;
    static final int COL_POST_HINT = 8;
    static final int COL_STICKIED = 9;
    static final int COL_OVER_18 = 10;
    static final int COL_AUTHOR_FL_TEXT = 11;
    static final int COL_SLFTXT_HTML = 12;
    static final int COL_SLFTXT = 13;
    static final int COL_CREATED_UTC = 14;
    static final int COL_URL = 15;
    static final int COL_IMG_PORT = 16;
    static final int COL_IMG_PORT_WIDTH = 17;
    static final int COL_IMG_PORT_HEIGHT = 18;
    static final int COL_IMG_LAND = 19;
    static final int COL_IMG_LAND_WIDTH = 20;
    static final int COL_IMG_LAND_HEIGHT = 21;

    @BindView(R.id.pager_subreddit) TextView pagerSubreddit;
    @BindView(R.id.pager_domain) TextView pagerDomain;
    @BindView(R.id.pager_title) TextView pagerTitle;
    @BindView(R.id.pager_author) TextView pagerAuthor;
    @BindView(R.id.pager_post_time) TextView pagerTime;
    @BindView(R.id.pager_position) TextView pagerPosition;
    @BindView(R.id.pager_image) ImageView pagerImage;
    @BindView(R.id.pager_slftxt) TextView slfTextView;
    @BindView(R.id.pager_num_comments) TextView pagerNumComments;
    @BindView(R.id.pager_score) TextView pagerScore;
    @BindView(R.id.comment_tree) TextView commentTree;
    @BindView(R.id.adView)
    AdView mAdView;

    private Unbinder unbinder;

    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    static Cursor mCursor;
    static Context mContext;

    public PlaceholderFragment() {
    }

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static PlaceholderFragment newInstance(int position, Bundle bundle, Cursor cursor,
                                                  Context context) {
        PlaceholderFragment fragment = new PlaceholderFragment();
        Bundle args = (Bundle) bundle.clone();
        args.putInt(Constants.EXTRA_LINK_POSITION, position);
        fragment.setArguments(args);
        mCursor = cursor;
        mContext = context;
        return fragment;
    }

    private void bindView(View rootView) {
        AdRequest adRequest = new AdRequest.Builder()
                .build();
        mAdView.loadAd(adRequest);

        int pos = getArguments().getInt(Constants.EXTRA_LINK_POSITION);

        if (mCursor == null || mCursor.isClosed() || !mCursor.moveToPosition(pos))
            return;

        int cnt = getArguments().getInt(Constants.EXTRA_LINK_COUNT);
        String subreddit = getArguments().getString(Constants.EXTRA_SUBREDDIT_NAME);

        pagerSubreddit.setText(Util.getSubredditNameWithR(mContext, subreddit));
        pagerDomain.setText(mCursor.getString(COL_DOMAIN));
        pagerTitle.setText(Html.fromHtml(mCursor.getString(COL_TITLE)));
        pagerAuthor.setText(Util.bold(mCursor.getString(COL_AUTHOR)));
        pagerTime.setText(Util.getRelativeLocalTimeFromUTCtime(mCursor.getInt(COL_CREATED_UTC)));
        pagerPosition.setText(Util.getLinkPositionString(mContext, pos, cnt));

        int imgWidth;
        int imgHeight;
        String imgUrl;
        if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            imgWidth = mCursor.getInt(COL_IMG_PORT_WIDTH);
            imgHeight = mCursor.getInt(COL_IMG_PORT_HEIGHT);
            imgUrl = mCursor.getString(COL_IMG_PORT);
        } else {
            imgWidth = mCursor.getInt(COL_IMG_LAND_WIDTH);
            imgHeight = mCursor.getInt(COL_IMG_LAND_HEIGHT);
            imgUrl = mCursor.getString(COL_IMG_LAND);
        }
        if (imgUrl != null && !imgUrl.isEmpty() && imgUrl.startsWith(Constants.HTTP_PREFIX)) {
            int screenWidth = Util.getDisplaySize(mContext).x;
            pagerImage.setVisibility(View.VISIBLE);
            RequestCreator rc = Picasso.get().load(imgUrl);
            if (screenWidth > 0)
                rc = rc.resize(screenWidth, screenWidth * imgHeight / imgWidth);
            rc.into(pagerImage);
        } else {
            pagerImage.setVisibility(View.GONE);
        }

        String slfText = mCursor.getString(COL_SLFTXT);
        String slfHtml = null;
        if (slfHtml != null && !slfHtml.isEmpty() && !slfHtml.equals("null")) {
            slfTextView.setVisibility(View.VISIBLE);
            slfTextView.setText(Html.fromHtml(slfHtml));
        } else if (slfText != null && !slfText.isEmpty() && !slfText.equals("null")) {
            slfTextView.setVisibility(View.VISIBLE);
            slfTextView.setText(Html.fromHtml(slfText)); //Just in case remove HTML entities
        } else {
            slfTextView.setVisibility(View.GONE);
        }

        int numComments = mCursor.getInt(COL_NUM_COMMENTS);
        pagerNumComments.setText(String.format(mContext.getString(R.string.num_commments), numComments));
        pagerScore.setText(Util.bold(mCursor.getString(COL_SCORE)));
        if (numComments > 0) {
            commentTree.setText(mContext.getString(R.string.loading_comments));
        }

        String linkId = mCursor.getString(COL_ID);
        bindCommentsToView(commentTree, subreddit, linkId);
    }

    private static void bindCommentsToView(final TextView commentTree, String subreddit, String linkId) {
        new RedditRestClient(mContext).beginRetrievingComments(subreddit, linkId,
                new RedditRestClient.JsonResultHandler() {
                    @Override
                    public void onSuccess(JSONObject response) {
                    }
                    public void onSuccess(JSONArray response) {
                        try {
                            SpannableStringBuilder text = new SpannableStringBuilder();
                            JSONArray comments = response.getJSONObject(1).getJSONObject("data").getJSONArray("children");
                            for (int i = 0; i < comments.length(); i++) {
                                JSONObject commentJson = comments.getJSONObject(i).getJSONObject("data");
                                text.append(getComment(2, commentJson));
                            }
                            text.append("\n\n");
                            commentTree.setText(text);
                        } catch (JSONException e) {
                            Log.e(TAG, e.getMessage(), e);
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onFailure(int errorCode) {
                    }
                    private CharSequence getComment(int level, JSONObject comment) {
                        SpannableStringBuilder text = new SpannableStringBuilder();
                        try {
                            if (!comment.isNull("author")) {
                                text.append(Util.underline(
                                        Util.bold(comment.getString("author") + " "),
                                        Util.italic(Util.getRelativeLocalTimeFromUTCtime(
                                                (long) comment.getInt("created_utc")) + " "),
                                        Util.bold(comment.getString("score"))));
                                text.append('\n');
                                text.append(Html.fromHtml(comment.getString("body")));
                                text.append('\n');
                                if (!comment.isNull("replies") &&
                                        (comment.get("replies") instanceof JSONObject)) {
                                    JSONObject replies = comment.getJSONObject("replies");
                                    if (!replies.isNull("data")) {
                                        JSONObject data = replies.getJSONObject("data");
                                        if (!data.isNull("children")) {
                                            JSONArray children = data.getJSONArray("children");
                                            for (int i = 0; i < children.length(); i++) {
                                                text.append(getComment(level + 1,
                                                        children.getJSONObject(i)
                                                                .getJSONObject("data")));
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, e.getMessage(), e);
                            e.printStackTrace();
                        }
                        return Util.indent(level * 4, text);
                        //return Util.indentDrawable(mContext, level * 4, text);
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_post_with_comments, container,
                false);
        unbinder = ButterKnife.bind(this, rootView);
        bindView(rootView);
        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}