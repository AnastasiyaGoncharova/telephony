package com.example.fioandphonenumber;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private CallReceiver callReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Проверка разрешений
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            startCallInterceptor();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void startCallInterceptor() {
        // Регистрация слушателя событий телефонного состояния
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        // Регистрация приемника вызовов
        callReceiver = new CallReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.PHONE_STATE");
        registerReceiver(callReceiver, intentFilter);
    }

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            super.onCallStateChanged(state, phoneNumber);
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    // Обработка входящего вызова
                    Log.d(TAG, "Входящий вызов: " + phoneNumber);

                    // Проверка номера в CRM и отображение имени, если номер существует
                    checkNumberInCRM(phoneNumber);
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    // Телефонный звонок завершен
                    break;
            }
        }
    };

    private void checkNumberInCRM(String phoneNumber) {
        // Выполнение сетевого запроса в фоновом потоке AsyncTask
        new NetworkRequestTask().execute(phoneNumber);
    }

    private class NetworkRequestTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String phoneNumber = params[0];
            try {
                String url = "https://crm.elcity.ru/api/v1/Contact?offset=0&maxSize=20";
                URL obj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) obj.openConnection();

                // метод запроса GET и заголовок X-Api-Key
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-Api-Key", "35394ef793765af61ffc73725315ff2f");

                // Получение ответа
                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Успешный запрос
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // Обработка и вывод информации о контакте
                    parseResponse(response.toString(), phoneNumber);

                } else {
                    // Ошибка запроса
                    return "Error: " + responseCode;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            // Обработка результата запроса
            if (result != null) {
                Log.d(TAG, result);
            }
        }
    }

    private void parseResponse(String response, String phoneNumber) {
        try {
            JSONObject json = new JSONObject(response);
            JSONArray contactsArray = json.getJSONArray("list");
            boolean contactFound = false;

            for (int i = 0; i < contactsArray.length(); i++) {
                JSONObject contactObject = contactsArray.getJSONObject(i);

                if (contactObject.has("phoneNumber")) {
                    String contactPhoneNumber = contactObject.getString("phoneNumber");

                    if (contactPhoneNumber.equals(phoneNumber)) {
                        String lastName = contactObject.optString("lastName", "");
                        String firstName = contactObject.optString("firstName", "");

                        displayContactInfo(firstName, lastName);
                        contactFound = true;
                        break;
                    }
                }
            }

            if (!contactFound) {
                displayContactInfo("", "");
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void displayContactInfo(String firstName, String lastName) {
        Log.d(TAG, "Фамилия: " + lastName);
        Log.d(TAG, "Имя: " + firstName);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Отмена регистрации приемника вызовов и слушателя событий
        if (callReceiver != null) {
            unregisterReceiver(callReceiver);
        }
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Проверка результатов запроса разрешения
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                startCallInterceptor();
            } else {
                Toast.makeText(this, "Разрешение на чтение телефонного состояния и журнала вызовов отклонено", Toast.LENGTH_SHORT).show();
            }
        }
    }
}