package com.example.fioandphonenumber;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = "CallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) {
            // Состояние неизвестно, выход из метода
            return;
        }

        if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
            // Обработка входящего звонка
            String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (phoneNumber != null) {
                Log.d(TAG, "Входящий вызов: " + phoneNumber);
                // Проверка номера в CRM и отображение имени, если номер существует
                checkNumberInCRM(phoneNumber);
            }
        }
    }

    private void checkNumberInCRM(String phoneNumber) {
        // Выполнение сетевого запроса в фоновом потоке AsyncTask
        new NetworkRequestTask().execute(phoneNumber);
    }

    private class NetworkRequestTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String phoneNumber = params[0];
            String url = "https://crm.elcity.ru/api/v1/Contact?offset=0&maxSize=20&phone=" + phoneNumber;

            String response = null;
            try {
                // Выполнение HTTP-запроса и получение ответа с информацией о контакте в формате JSON
                URL apiUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    StringBuilder responseBuilder = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    in.close();
                    response = responseBuilder.toString();
                }

                connection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Ошибка при выполнении HTTP-запроса: " + e.getMessage());
            }

            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            // Обработка результата выводом фамилии и имени, если пришли данные
            try {
                if (result != null) {
                    JSONObject responseObj = new JSONObject(result);
                    int total = responseObj.optInt("total");
                    JSONArray contactsArray = responseObj.optJSONArray("list");
                    if (total > 0 && contactsArray != null && contactsArray.length() > 0) {
                        JSONObject contact = contactsArray.getJSONObject(0);
                        String firstName = contact.optString("firstName", "");
                        String lastName = contact.optString("lastName", "");
                        Log.d(TAG, "Фамилия: " + lastName);
                        Log.d(TAG, "Имя: " + firstName);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d(TAG, "Ошибка при обработке результатов запроса: " + e.getMessage());
            }
        }
    }
}