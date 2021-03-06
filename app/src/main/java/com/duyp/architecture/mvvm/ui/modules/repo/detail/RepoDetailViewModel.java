package com.duyp.architecture.mvvm.ui.modules.repo.detail;

import android.arch.lifecycle.MutableLiveData;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import com.duyp.androidutils.AlertUtils;
import com.duyp.androidutils.realm.LiveRealmObject;
import com.duyp.androidutils.rx.Rx;
import com.duyp.architecture.mvvm.App;
import com.duyp.architecture.mvvm.data.local.user.UserManager;
import com.duyp.architecture.mvvm.data.model.BranchesModel;
import com.duyp.architecture.mvvm.data.model.RealmString;
import com.duyp.architecture.mvvm.data.model.Repo;
import com.duyp.architecture.mvvm.data.model.RepoDetail;
import com.duyp.architecture.mvvm.data.model.User;
import com.duyp.architecture.mvvm.data.remote.RepoService;
import com.duyp.architecture.mvvm.data.repository.RepoDetailRepo;
import com.duyp.architecture.mvvm.helper.BundleConstant;
import com.duyp.architecture.mvvm.ui.base.BaseViewModel;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by duypham on 10/29/17.
 *
 */

public class RepoDetailViewModel extends BaseViewModel{

    private final RepoDetailRepo repoDetailRepo;

    @Nullable
    @Getter private LiveRealmObject<RepoDetail> data;

    @NonNull @Getter private MutableLiveData<Boolean> onDataReady = new MutableLiveData<>();

    @Getter @Setter
    @Tab private int currentTab = Tab.CODE;

    private String owner;
    private String repoName;

    @Getter private final MutableLiveData<Boolean> watchStatus = new MutableLiveData<>();
    @Getter private final MutableLiveData<Boolean> starStatus = new MutableLiveData<>();
    @Getter private final MutableLiveData<Boolean> folkStatus = new MutableLiveData<>();
    @Getter private final MutableLiveData<List<String>> topics = new MutableLiveData<>();

    @Getter private final MutableLiveData<List<BranchesModel>> branchesData = new MutableLiveData<>();

    private final RepoService repoService;

    private boolean isCollaborator;

    @Inject
    public RepoDetailViewModel(UserManager userManager, RepoDetailRepo repo, RepoService repoService) {
        super(userManager);
        this.repoService = repoService;
        this.repoDetailRepo = repo;
        watchStatus.setValue(null);
        starStatus.setValue(null);
        folkStatus.setValue(false);
    }

    @Override
    protected void onFirsTimeUiCreate(@Nullable Bundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Must pass repo data to use this fragment");
        }
        Repo repo = bundle.getParcelable(BundleConstant.EXTRA);
        if (repo != null) {
            data = repoDetailRepo.initRepo(repo);
            owner = repo.getOwner().getLogin();
            repoName = repo.getName();
            assert data != null;
            topics.setValue(Rx.map(data.getData().getTopics(), RealmString::getValue));
            onDataReady.setValue(true);
        } else {
            repoName = bundle.getString(BundleConstant.ID);
            owner = bundle.getString(BundleConstant.EXTRA_TWO);
            initRepo(owner, repoName);
        }

        if (data == null) {
            refresh(true, 100);
        } else {
            refresh(false, 300);
        }

        new Handler(Looper.myLooper()).postDelayed(() -> {
            checkStarred();
            checkWatched();
            getBranches(1);
            checkCollaborator();
        }, 100);
    }

    private void initRepo(String owner, String repoName) {
        data = repoDetailRepo.initRepo(owner, repoName);
        if (data != null) {
            onDataReady.setValue(true);
        }
    }

    public void refresh(boolean showProgress, int delay) {
        new Handler(Looper.myLooper()).postDelayed(() -> {
            execute(showProgress, repoDetailRepo.getRepo(owner, repoName), repoDetail -> {
                if (data == null) {
                    initRepo(owner, repoName);
                }
            });
        }, delay);
    }

    private void checkWatched() {
        execute(false, false, repoService.isWatchingRepo(owner, repoName), repoSubscriptionModel -> {
               watchStatus.setValue(repoSubscriptionModel.isSubscribed());
        }, errorEntity -> {
            watchStatus.setValue(false);
        });
    }

    private void checkStarred() {
        execute(false, false, repoService.checkStarring(owner, repoName), booleanResponse -> {
            starStatus.setValue(booleanResponse.code() == 204);
        }, errorEntity -> {
            starStatus.setValue(false);
        });
    }

    private void checkCollaborator() {
        String currentUser = repoDetailRepo.getCurrentUserLogin();
        if (currentUser == null) {
            isCollaborator = false;
        } else {
            execute(false, false, repoService.isCollaborator(owner, repoName, currentUser), booleanResponse -> {
                isCollaborator = booleanResponse.code() == 204;
            }, errorEntity -> {
                isCollaborator = false;
            });
        }
    }

    public void getBranches(int page) {
        execute(false, false, repoService.getBranches(owner, repoName, page), branches -> {
            if (branchesData.getValue() == null) {
                branchesData.setValue(new ArrayList<>());
            }
            List<BranchesModel> list = branchesData.getValue();
            if (page == 1) {
                // noinspection ConstantConditions
                list.clear();
            }
            // noinspection ConstantConditions
            list.addAll(branches.getItems());
            branchesData.setValue(list);
            if (branches.getLast() > page) {
                getBranches(page + 1);
            }
        }, errorEntity -> {});
    }

    public void starRepoClick() {
        if (starStatus.getValue() != null) {
            boolean star = !starStatus.getValue();
            repoDetailRepo.updateStarred(star);
            starStatus.setValue(star);
            execute(false, true, star ? repoService.starRepo(owner, repoName) : repoService.unstarRepo(owner, repoName), booleanResponse -> {
                if (booleanResponse.code() != 204) {
                    repoDetailRepo.updateStarred(!star);
                    starStatus.setValue(!star); // reverse if error
                }
            }, errorEntity -> {
                repoDetailRepo.updateStarred(!star);
                starStatus.setValue(!star); // reverse if error
            });
        }
    }

    public void watchRepoClick() {
        if (watchStatus.getValue() != null) {
            boolean watch = !watchStatus.getValue();
            repoDetailRepo.updateWatched(watch);
            watchStatus.setValue(watch);
            execute(false,true, watch ? repoService.watchRepo(owner, repoName) : repoService.unwatchRepo(owner, repoName), booleanResponse -> {
                if (booleanResponse.code() != 204) {
                    repoDetailRepo.updateWatched(!watch);
                    watchStatus.setValue(!watch); // reverse if error
                }
            }, errorEntity -> {
                repoDetailRepo.updateWatched(!watch);
                watchStatus.setValue(!watch); // reverse if error
            });
        }
    }

    public void forkRepoClick() {
        AlertUtils.showToastShortMessage(App.getInstance(), "Coming soon...");
    }

    public void pinRepoClick() {
        AlertUtils.showToastShortMessage(App.getInstance(), "Coming soon...");
    }

    public void wikiClick() {
        AlertUtils.showToastShortMessage(App.getInstance(), "Coming soon...");
    }

    public boolean isOwnerOrCollaborator() {
        User user = getUserManager().getCurrentUser();
        if (user == null) {
            return false;
        }
        return owner.equals(user.getLogin()) || isCollaborator;
    }

    @Nullable
    public RepoDetail getRepoDetail() {
        if (data != null) {
            return data.getData();
        }
        return null;
    }
}
