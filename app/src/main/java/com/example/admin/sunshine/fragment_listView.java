package com.example.admin.sunshine;


import android.app.Fragment;
import android.app.SharedElementCallback;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;

public class fragment_listView extends Fragment implements AdapterView.OnItemClickListener {
        ArrayAdapter<String> myForeCast;
        private static final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        /* The date/time conversion code is going to be moved outside the asynctask later,
                * so for convenience we're breaking it out into its own method now.
                */
        private String getReadableDateString(long time) {
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            SharedPreferences shref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = shref.getString(getString(R.string.pref_unit_key), getString(R.string.pref_unit_metric));

            if (unitType.equals(getString(R.string.pref_unit_imperial))) {
                high = (high * 1.8) + 32;
                low = (low + 1.8) + 32;
            } else if (!unitType.equals(getString(R.string.pref_unit_metric))) {
                Log.d(LOG_TAG, "UNIT TYPE NOT FOUND " + unitType);
            }


            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

    @Override
    public void onStart() {
        super.onStart();
        WeatherRefresh();

    }

    @Override
    public void setExitSharedElementCallback(SharedElementCallback callback) {
        super.setExitSharedElementCallback(callback);
    }

    /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         * <p/>
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";

            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";
            Log.v(LOG_TAG,forecastJsonStr);

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for (int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            for (String s : resultStrs) {
                Log.v(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;

        }

        public fragment_listView() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            super.onCreateOptionsMenu(menu, inflater);
            inflater.inflate(R.menu.fragment_layout, menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {

            int id;
            id = item.getItemId();
            if (id == R.id.action_refresh) {
                    WeatherRefresh();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
private void WeatherRefresh() {
    FetchWeatherTask weatherTask = new FetchWeatherTask();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
    String location = pref.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        Log.v(LOG_TAG, pref.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default)));
        weatherTask.execute(location);
    }

    @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_layout, container, false);
            ListView listView = (ListView) rootView.findViewById(R.id.listView);
            myForeCast = new ArrayAdapter<String>(getActivity(), R.layout.listview_item, R.id.textView_listItem, new ArrayList<String>());
            listView.setAdapter(myForeCast);
            listView.setOnItemClickListener(this);

            return rootView;

        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String forecast = (String) parent.getItemAtPosition(position);
            Intent intent = new Intent(getActivity(), DetailActivity.class).putExtra(Intent.EXTRA_TEXT, forecast);
            startActivity(intent);
        }

        class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

            @Override
            protected String[] doInBackground(String... params) {

                // These two need to be declared outside the try/catch
                // so that they can be closed in the finally block.
                HttpURLConnection urlConnection = null;
                BufferedReader reader = null;

                String format = "json";
                String units = "metric";
                int numDate = 7;
                // Will contain the raw JSON response as a string.
                String forecastJsonStr = null;

                try {
                    // Construct the URL for the OpenWeatherMap query
                    // Possible parameters are available at OWM's forecast API page, at
                    // http://openweathermap.org/API#forecast

                    final String FORECAST_BASE_URL="http://api.openweathermap.org/data/2.5/forecast/daily?";
                    final String QUERY_PARAM="q";
                    final String FORMAT_PARAM="mode";
                    final String DAYS_PARAM="cnt";
                    final String UNITS_PARAM="units";
                    Uri.Builder builtUri= Uri.parse(FORECAST_BASE_URL).buildUpon().appendQueryParameter(QUERY_PARAM,params[0]).appendQueryParameter(FORMAT_PARAM,format).appendQueryParameter(UNITS_PARAM,units)
                            .appendQueryParameter(DAYS_PARAM, Integer.toString(numDate));
                    String r= builtUri.toString()+"&APPID=680aaaa5258e15d43253263b8c9da7f3";
                    Uri built=Uri.parse(r);

                    URL url = new URL(r.toString());

                    // Create the request to OpenWeatherMap, and open the connection
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.connect();

                    // Read the input stream into a String
                    InputStream inputStream = urlConnection.getInputStream();
                    StringBuffer buffer = new StringBuffer();
                    if (inputStream == null) {
                        // Nothing to do.
                        forecastJsonStr = null;
                    }
                    reader = new BufferedReader(new InputStreamReader(inputStream));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                        // But it does make debugging a *lot* easier if you print out the completed
                        // buffer for debugging.
                        buffer.append(line + "\n");
                    }

                    if (buffer.length() == 0) {
                        // Stream was empty.  No point in parsing.
                        forecastJsonStr = null;
                    }
                    forecastJsonStr = buffer.toString();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Error ", e);
                    // If the code didn't successfully get the weather data, there's no point in attempting
                    // to parse it.
                    forecastJsonStr = null;
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (final IOException e) {
                            Log.e(LOG_TAG, "Error closing stream", e);
                        }
                    }
                }
                try {
                    Log.v(LOG_TAG,"I AM IN doInBackground");
                    return getWeatherDataFromJson(forecastJsonStr, numDate);
                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.getMessage(), e);
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String[] strings) {
                if(strings!=null)
                {
                    Log.v(LOG_TAG,"I AM IN onPostExecute");
                    myForeCast.clear();
                    for(String dayForcastStr : strings)
                    {
                        myForeCast.add(dayForcastStr);

                    }
                }

            }
        }
    }