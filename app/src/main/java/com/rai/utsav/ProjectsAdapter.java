package com.rai.utsav;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.amazonaws.demo.appsync.ListProjectsQuery;
import com.rai.utsav.R;

import java.util.List;

public class ProjectsAdapter extends BaseAdapter {

    private final Context mContext;
    private LayoutInflater mInflater;
    private List<ListProjectsQuery.Item> projects;

    public ProjectsAdapter(Context context, List<ListProjectsQuery.Item> projects){
        this.mContext = context;
        this.projects = projects;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setProjects(List<ListProjectsQuery.Item> posts){
        this.projects = posts;
    }

    @Override
    public int getCount() {
        return projects.size();
    }

    @Override
    public Object getItem(int i) {
        return projects.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.project_list_view, parent, false);
            holder = new ViewHolder();
            holder.nameTextView = (TextView) convertView.findViewById(R.id.projectTitle);
            holder.timeTextView = (TextView) convertView.findViewById(R.id.projectAuthor);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ListProjectsQuery.Item post = (ListProjectsQuery.Item) getItem(i);
        final SpannableStringBuilder sb = new SpannableStringBuilder("Project Title: " + post.fragments().project().name());

// Span to set text color to some RGB value
        final ForegroundColorSpan fcs = new ForegroundColorSpan(Color.rgb(244, 67, 54));

// Span to make text bold
        final StyleSpan bss = new StyleSpan(android.graphics.Typeface.BOLD);

// Set the text color for first 4 characters
        sb.setSpan(fcs, 0, 14, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

// make them also bold
        sb.setSpan(bss, 0, 14, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        holder.nameTextView.setText(sb);
        holder.timeTextView.setText("Time: " + post.fragments().project().when());

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ViewProjectActivity.startActivity(view.getContext(), post.fragments().project());
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        public TextView nameTextView;
        public TextView timeTextView;
    }
}
