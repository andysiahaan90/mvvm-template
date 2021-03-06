package com.duyp.architecture.mvvm.data.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

import io.realm.RealmList;
import io.realm.RealmObject;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Commit extends RealmObject implements Parcelable {

    public String sha;
    public String url;
    public String message;
    public User author;
    public User committer;
    public User tree;
    public @SerializedName("distinct") boolean distincted;
    public RealmList<Commit> parents;
    public int commentCount;
    String ref;

    @SerializedName("commit")
    GitCommitModel gitCommit;

    @Nullable
    public User getUser() {
        return author != null ? author : gitCommit.getAuthor();
    }

    public Commit() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.sha);
        dest.writeString(this.url);
        dest.writeString(this.message);
        dest.writeParcelable(this.author, flags);
        dest.writeParcelable(this.committer, flags);
        dest.writeParcelable(this.tree, flags);
        dest.writeByte(this.distincted ? (byte) 1 : (byte) 0);
        dest.writeTypedList(this.parents);
        dest.writeInt(this.commentCount);
        dest.writeString(this.ref);
        dest.writeParcelable(this.gitCommit, flags);
    }

    protected Commit(Parcel in) {
        this.sha = in.readString();
        this.url = in.readString();
        this.message = in.readString();
        this.author = in.readParcelable(User.class.getClassLoader());
        this.committer = in.readParcelable(User.class.getClassLoader());
        this.tree = in.readParcelable(User.class.getClassLoader());
        this.distincted = in.readByte() != 0;

        this.parents = new RealmList<>();
        ArrayList<Commit> list = in.createTypedArrayList(Commit.CREATOR);
        if (list != null && list.size() > 0) {
            parents.addAll(list);
        }

        this.commentCount = in.readInt();
        this.ref = in.readString();
        this.gitCommit = in.readParcelable(GitCommitModel.class.getClassLoader());
    }

    public static final Creator<Commit> CREATOR = new Creator<Commit>() {
        @Override
        public Commit createFromParcel(Parcel source) {
            return new Commit(source);
        }

        @Override
        public Commit[] newArray(int size) {
            return new Commit[size];
        }
    };
}