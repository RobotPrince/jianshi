package com.wingjay.jianshi.manager;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.Toast;

import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.wingjay.jianshi.Constants;
import com.wingjay.jianshi.R;
import com.wingjay.jianshi.bean.User;
import com.wingjay.jianshi.db.model.Diary_Table;
import com.wingjay.jianshi.db.model.PushData_Table;
import com.wingjay.jianshi.network.JsonDataResponse;
import com.wingjay.jianshi.network.UserService;
import com.wingjay.jianshi.prefs.UserPrefs;
import com.wingjay.jianshi.sync.SyncManager;
import com.wingjay.jianshi.sync.SyncService;
import com.wingjay.jianshi.ui.MainActivity;
import com.wingjay.jianshi.ui.SignupActivity;
import com.wingjay.jianshi.util.RxUtil;

import javax.inject.Inject;

import dagger.Lazy;
import rx.functions.Action1;
import timber.log.Timber;

/**
 * User Management.
 */

public class UserManager {

  @Inject
  Lazy<UserPrefs> userPrefsLazy;

  @Inject
  UserService userService;

  @Inject
  UserPrefs userPrefs;

  @Inject
  UserManager() {}

  public void login(final Context context, @NonNull String email, @NonNull String password) {
    userService.login(email, password)
        .compose(RxUtil.<JsonDataResponse<User>>normalSchedulers())
        .subscribe(new Action1<JsonDataResponse<User>>() {
          @Override
          public void call(JsonDataResponse<User> userJsonDataResponse) {
            if (userJsonDataResponse.getRc() == Constants.ServerResultCode.RESULT_OK) {
              User user = userJsonDataResponse.getData();
              if (user == null || user.getId() <= 0) {
                throw new RuntimeException(userJsonDataResponse.getMsg());
              } else if (TextUtils.isEmpty(user.getEncryptedToken())) {
                throw new RuntimeException(userJsonDataResponse.getMsg());
              }
              userPrefsLazy.get().setAuthToken(user.getEncryptedToken());
              userPrefsLazy.get().setUser(user);

              context.startActivity(MainActivity.createIntent(context));
            } else {
              Toast.makeText(context, userJsonDataResponse.getMsg(), Toast.LENGTH_SHORT).show();
            }
          }
        }, new Action1<Throwable>() {
          @Override
          public void call(Throwable e) {
            Timber.e(e, "login failure");
            Toast.makeText(context, context.getString(R.string.login_failure),
                Toast.LENGTH_SHORT).show();
          }
        });
  }

  public void signup(final Context context, @NonNull String email, @NonNull String password) {
    userService.signup(email, password)
        .compose(RxUtil.<JsonDataResponse<User>>normalSchedulers())
        .subscribe(new Action1<JsonDataResponse<User>>() {
          @Override
          public void call(JsonDataResponse<User> userJsonDataResponse) {
            if (userJsonDataResponse.getRc() == Constants.ServerResultCode.RESULT_OK) {
              User user = userJsonDataResponse.getData();
              if (user == null || user.getId() <= 0) {
                throw new RuntimeException(userJsonDataResponse.getMsg());
              } else if (TextUtils.isEmpty(user.getEncryptedToken())) {
                throw new RuntimeException(userJsonDataResponse.getMsg());
              }

              userPrefsLazy.get().setAuthToken(user.getEncryptedToken());
              userPrefsLazy.get().setUser(user);
              context.startActivity(MainActivity.createIntent(context));
            } else {
              Toast.makeText(context, userJsonDataResponse.getMsg(), Toast.LENGTH_SHORT).show();
            }
          }
        }, new Action1<Throwable>() {
          @Override
          public void call(Throwable e) {
            Timber.e(e, "signup failure");
            Toast.makeText(context, context.getString(R.string.signup_failure),
                Toast.LENGTH_SHORT).show();
          }
        });
  }

  public void logout(final @NonNull Context context) {
    boolean success = false;
    final ProgressDialog dialog = ProgressDialog.show(context, "", "注销中");
    if (SQLite.select().from(PushData_Table.class).queryList().size() > 0) {
      SyncService.syncImmediately(context, new SyncManager.SyncResultListener() {
        @Override
        public void onSuccess() {
          dialog.dismiss();
          doLogout(context);
        }

        @Override
        public void onFailure() {
          dialog.dismiss();
          AlertDialog.Builder builder = new AlertDialog.Builder(context)
              .setTitle("确定要注销？你还有数据未同步，请保持网络畅通")
              .setPositiveButton("是", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                  SyncService.syncImmediately(context, null);
                  doLogout(context);
                }
              })
              .setNegativeButton("否", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                  dialogInterface.dismiss();
                }
              });
          builder.show();
        }
      });
    } else {
      doLogout(context);
    }
  }

  private void doLogout(final @NonNull Context context) {
    userPrefs.clearAuthToken();
    userPrefs.clearUser();
    SQLite.delete().from(PushData_Table.class).execute();
    SQLite.delete().from(Diary_Table.class).execute();
    context.startActivity(SignupActivity.createIntent(context));
  }
}