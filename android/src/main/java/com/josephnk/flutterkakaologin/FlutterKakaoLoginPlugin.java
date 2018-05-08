package com.josephnk.flutterkakaologin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.kakao.auth.ApprovalType;
import com.kakao.auth.AuthType;
import com.kakao.auth.IApplicationConfig;
import com.kakao.auth.ISessionCallback;
import com.kakao.auth.ISessionConfig;
import com.kakao.auth.KakaoAdapter;
import com.kakao.auth.KakaoSDK;

import com.kakao.auth.Session;
import com.kakao.auth.authorization.accesstoken.AccessToken;
import com.kakao.network.ErrorResult;
import com.kakao.usermgmt.UserManagement;
import com.kakao.usermgmt.callback.LogoutResponseCallback;
import com.kakao.usermgmt.callback.MeV2ResponseCallback;
import com.kakao.usermgmt.response.MeV2Response;
import com.kakao.util.exception.KakaoException;

import java.util.HashMap;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;


/**
 * FlutterKakaoLoginPlugin
 */
public class FlutterKakaoLoginPlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener {

  private static final String CHANNEL_NAME = "flutter_kakao_login";

  private static final String METHOD_LOG_IN = "logIn";
  private static final String METHOD_LOG_OUT = "logOut";
  private static final String METHOD_GET_CURRENT_ACCESS_TOKEN = "getCurrentAccessToken";

  private static final String LOG_TAG = "KakaoTalkPlugin";

  private Activity currentActivity;
  private SessionCallback sessionCallback;

  /**
   * Plugin registration.
   */
  public static void registerWith(Registrar registrar) {
    final FlutterKakaoLoginPlugin plugin = new FlutterKakaoLoginPlugin(registrar);
    final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL_NAME);
    channel.setMethodCallHandler(plugin);
    registrar.addActivityResultListener(plugin);
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    final Result _result = result;

    switch (call.method) {
      case METHOD_LOG_IN:
        // ensure old session was closed
        Session.getCurrentSession().close();

        sessionCallback = new SessionCallback(_result);
        Session.getCurrentSession().addCallback(sessionCallback);
        Session.getCurrentSession().open(AuthType.KAKAO_TALK, currentActivity);
        break;
      case METHOD_LOG_OUT:
        UserManagement.getInstance().requestLogout(new LogoutResponseCallback() {
          @Override
          public void onCompleteLogout() {
            _result.success(new HashMap<String, String>() {{
              put("status", "loggedOut");
            }});
          }
        });
        break;
      case METHOD_GET_CURRENT_ACCESS_TOKEN:
        AccessToken tokenInfo = Session.getCurrentSession().getTokenInfo();
        String accessToken = tokenInfo.getAccessToken();
        _result.success(accessToken);
        break;

      default:
        result.notImplemented();
        break;
    }
  }

  /**
   * PluginRegistry.ActivityResultListener.
   */
  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.v(LOG_TAG, "onActivityResult requestCode: " + requestCode + " resultCode: " + resultCode + " data: " + data);
    if (Session.getCurrentSession().handleActivityResult(requestCode, resultCode, data)){
      return false;
    }
    return false;
  }

  /**
   * Initialize
   */
  private FlutterKakaoLoginPlugin(Registrar registrar) {
    //applicationContext = registrar.context().getApplicationContext();
    currentActivity = registrar.activity();
    try {
      KakaoSDK.init(new KakaoSDKAdapter(currentActivity));
    } catch(RuntimeException e){
      Log.e("kakao init error", "error", e);
    }
  }

  /**
   * Get current activity
   */
  public Activity getCurrentActivity() {
    return currentActivity;
  }

  /**
   * Set current activity
   */
  public void setCurrentActivity(Activity activity) {
    currentActivity = activity;
  }

  /**
   * Class SessonCallback
   */
  private class SessionCallback implements ISessionCallback {
    private Result result;

    public SessionCallback(Result result) {
      Log.v(LOG_TAG, "kakao : SessionCallback create");
      this.result = result;
    }

    private void removeCallback() {
      Session.getCurrentSession().removeCallback(sessionCallback);
    }

    @Override
    public void onSessionOpened() {
      Log.v(LOG_TAG, "kakao : SessionCallback.onSessionOpened");
      UserManagement.getInstance().me(new MeV2ResponseCallback() {
        @Override
        public void onSessionClosed(ErrorResult errorResult) {
          removeCallback();

          final String errorMessage = errorResult.getErrorMessage();
          String message = "failed to get user info. msg=" + errorResult;
          Log.v(LOG_TAG, "kakao : onSessionClosed " + message);

          result.success(new HashMap<String, String>() {{
            put("status", "error");
            put("errorMessage", errorMessage);
          }});
        }

        @Override
        public void onSuccess(MeV2Response resultKakao) {
          removeCallback();

          final Long userID = resultKakao.getId();
          final String userEmail = resultKakao.getKakaoAccount().getEmail();
          Log.v(LOG_TAG, "kakao : onSuccess " + "userID: " + userID + " and userEmail: " + userEmail);

          result.success(new HashMap<String, String>() {{
            put("status", "loggedIn");
            put("userID", userID.toString());
            put("userEmail", userEmail);
          }});
        }
      });
    }

    @Override
    public void onSessionOpenFailed(KakaoException exception) {
      removeCallback();

      if (exception != null) {
        final String errorMessage = exception.toString();
        Log.v(LOG_TAG, "kakao : onSessionOpenFailed " + errorMessage);
        result.success(new HashMap<String, String>() {{
          put("status", "error");
          put("errorMessage", errorMessage);
        }});
      }
    }
  }


  /**
   * Class KakaoSDKAdapter
   */
  private static class KakaoSDKAdapter extends KakaoAdapter {

    private final Activity currentActivity;

    public KakaoSDKAdapter(Activity activity) {
      this.currentActivity = activity;
    }

    @Override
    public ISessionConfig getSessionConfig() {
      return new ISessionConfig() {
        @Override
        public AuthType[] getAuthTypes() {
          return new AuthType[]{AuthType.KAKAO_TALK};
        }

        @Override
        public boolean isUsingWebviewTimer() {
          return false;
        }

        @Override
        public boolean isSecureMode() {
          return false;
        }

        @Override
        public ApprovalType getApprovalType() {
          return ApprovalType.INDIVIDUAL;
        }

        @Override
        public boolean isSaveFormData() {
          return false;
        }
      };
    }

    @Override
    public IApplicationConfig getApplicationConfig() {
      return new IApplicationConfig() {
        public Activity getTopActivity() {
          return currentActivity;
        }

        @Override
        public Context getApplicationContext() {
          return currentActivity.getApplicationContext();
        }
      };
    }
  }


}