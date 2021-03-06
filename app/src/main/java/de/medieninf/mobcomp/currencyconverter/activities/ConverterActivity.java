package de.medieninf.mobcomp.currencyconverter.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.List;

import de.medieninf.mobcomp.currencyconverter.R;
import de.medieninf.mobcomp.currencyconverter.exceptions.NetworkConnectionException;
import de.medieninf.mobcomp.currencyconverter.logic.CurrencyRateProviderImpl;
import de.medieninf.mobcomp.currencyconverter.logic.interfaces.CurrencyRateProvider;
import de.medieninf.mobcomp.currencyconverter.persistence.db.CurrencyDatabaseHelper;
import de.medieninf.mobcomp.currencyconverter.persistence.db.schema.CurrencyRatesTbl;
import de.medieninf.mobcomp.currencyconverter.persistence.interfaces.LoadManager;
import de.medieninf.mobcomp.currencyconverter.util.CurrencyConverterUtil;
import de.medieninf.mobcomp.currencyconverter.util.CurrencyEntryUtil;


public class ConverterActivity extends ActionBarActivity {
    //declare static variables
    private final static String TAG = ConverterActivity.class.getSimpleName();
    private static String CHECKBOX_PREFERENCES;
    private static String DEFAULT_AMOUNT_PREFERENCES;
    private static String LAST_AMOUNT_PREFERENCES;
    private static String DEFAULT_START_CURRENCY_PREFERENCES;
    private static String DEFAULT_TARGET_CURRENCY_PREFERENCES;
    private static String LAST_START_CURRENCY_PREFERENCES;
    private static String LAST_TARGET_CURRENCY_PREFERENCES;
    private static String DECIMAL_PLACES_PREFERENCES;
    // declare widgets
    private Button btnReset;
    private Toast toastError;
    private Toast toastRatesUpToDate;
    private Spinner spinnerStartCurrency;
    private Spinner spinnerTargetCurrency;
    private EditText etStartAmount;
    private EditText etTargetAmount;
    private TextView tvTimestamp;
    private CurrencyRateProvider currencyRateProvider;
    private String referencedCurrency;
    private ProgressDialog pDialog;
    // declare listener
    private View.OnClickListener resetListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.v(TAG, "onResetListener");
            TextKeyListener.clear(etStartAmount.getText());
        }
    };
    private TextWatcher twStartAmount = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            Log.v(TAG, "afterTextChanged ");
            setText(etTargetAmount, selectedStartCurrency, selectedTargetCurrency, etStartAmount.getText().toString());
        }
    };
    private TextWatcher twTargetAmount = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            Log.v(TAG, "afterTextChanged");
            setText(etStartAmount, selectedTargetCurrency, selectedStartCurrency, etTargetAmount.getText().toString());
        }
    };
    private AdapterView.OnItemSelectedListener avoisl = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Log.v(TAG, "onItemSelected");
            final String selected = (String) parent.getSelectedItem();
            final int parentId = parent.getId();
            if (spinnerStartCurrency != null && parentId == spinnerStartCurrency.getId()) {
                selectedStartCurrency = selected;
            } else if (spinnerTargetCurrency != null && parentId == spinnerTargetCurrency.getId()) {
                selectedTargetCurrency = selected;
            }

            currencyChanged();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            Log.v(TAG, "onNothingSelected");
        }
    };
    // declare other variables
    private Resources res;
    private SharedPreferences prefs;
    private String tvDate;
    private String selectedStartCurrency;
    private String selectedTargetCurrency;
    private boolean setTextFlag;
    private int scale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        setContentView(R.layout.activity_converter);
        preInit();
        new InitTask().execute();
    }

    @Override
     public boolean onCreateOptionsMenu(Menu menu) {
        Log.v(TAG, "onCreateOptionMenu");
        MenuInflater inflator = getMenuInflater();
        inflator.inflate(R.menu.menu_converter, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v(TAG, "onOptionsItemSelected");
        switch(item.getItemId()) {
            case R.id.action_update:
                pDialog = ProgressDialog.show(this, res.getString(R.string.progress_headline), res.getString(R.string.progress_text), true);
                new UpdateTask().execute(LoadManager.LoaderType.NETWORK);
                return true;
            case R.id.action_revert:
                pDialog = ProgressDialog.show(this, res.getString(R.string.progress_headline), res.getString(R.string.progress_text), true);
                new UpdateTask().execute(LoadManager.LoaderType.INIT);
                return true;
            case R.id.action_prefs:
                Intent intent = new Intent(this, SetPreferenceActivity.class);
                List<CharSequence> list = currencyRateProvider.getCurrencies();
                intent.putExtra("currencies", list.toArray(new String[list.size()]));
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        super.onPause();
        if(prefs != null) {
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString(LAST_AMOUNT_PREFERENCES,etStartAmount.getText().toString());
            editor.putString(LAST_START_CURRENCY_PREFERENCES,selectedStartCurrency);
            editor.putString(LAST_TARGET_CURRENCY_PREFERENCES,selectedTargetCurrency);
            editor.commit();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG, "onStop");
    }

    @Override
      protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
    }

    private void preInit() {
        Log.v(TAG, "preInit");
        res = getResources();
        CHECKBOX_PREFERENCES = res.getString(R.string.checkbox_preference);
        LAST_AMOUNT_PREFERENCES = res.getString(R.string.last_amount_preference);
        LAST_START_CURRENCY_PREFERENCES = res.getString(R.string.last_start_currency_preference);
        LAST_TARGET_CURRENCY_PREFERENCES = res.getString(R.string.last_target_currency_preference);
        DEFAULT_AMOUNT_PREFERENCES = res.getString(R.string.default_amount_preference);
        DEFAULT_START_CURRENCY_PREFERENCES = res.getString(R.string.default_start_currency_preference);
        DEFAULT_TARGET_CURRENCY_PREFERENCES = res.getString(R.string.default_target_currency_preference);
        DECIMAL_PLACES_PREFERENCES = res.getString(R.string.decimal_places_preference);
        referencedCurrency = res.getString(R.string.reference_currency);
        tvDate = res.getString(R.string.tv_date);
        // instantiate widgets
        pDialog = ProgressDialog.show(this, res.getString(R.string.progress_headline), res.getString(R.string.progress_text), true);
        btnReset = (Button) findViewById(R.id.btn_reset);
        spinnerStartCurrency = (Spinner) findViewById(R.id.spinner_start_currency);
        spinnerTargetCurrency = (Spinner) findViewById(R.id.spinner_target_currency);
        etStartAmount = (EditText) findViewById(R.id.et_start_amount);
        etStartAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etTargetAmount = (EditText) findViewById(R.id.et_target_amount);
        tvTimestamp = (TextView) findViewById(R.id.tv_timestamp);
        toastRatesUpToDate = Toast.makeText(ConverterActivity.this, R.string.msg_rates_up_to_date, Toast.LENGTH_SHORT);
        toastError = Toast.makeText(ConverterActivity.this, R.string.error_msg_network, Toast.LENGTH_SHORT);
        etTargetAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        // set listener
        btnReset.setOnClickListener(resetListener);
        etStartAmount.addTextChangedListener(twStartAmount);
        etTargetAmount.addTextChangedListener(twTargetAmount);
        spinnerStartCurrency.setOnItemSelectedListener(avoisl);
        spinnerTargetCurrency.setOnItemSelectedListener(avoisl);
    }

    private void postInit() {
        Log.v(TAG,"postInit");

        // load preferences
        prefs = getSharedPreferences("mySharedPrefs",MODE_PRIVATE);
        final boolean prefsActivated = prefs.getBoolean(CHECKBOX_PREFERENCES, false);
        String startCurrency;
        String targetCurrency;
        String inputAmount;
        if(prefsActivated) {
            startCurrency = prefs.getString(DEFAULT_START_CURRENCY_PREFERENCES,null);
            targetCurrency = prefs.getString(DEFAULT_TARGET_CURRENCY_PREFERENCES,null);
            inputAmount = prefs.getString(DEFAULT_AMOUNT_PREFERENCES,null);
        } else {
            startCurrency = prefs.getString(LAST_START_CURRENCY_PREFERENCES,null);
            targetCurrency = prefs.getString(LAST_TARGET_CURRENCY_PREFERENCES,null);
            inputAmount = prefs.getString(LAST_AMOUNT_PREFERENCES,null);
        }
        scale = Integer.parseInt(prefs.getString(DECIMAL_PLACES_PREFERENCES, res.getString(R.string.default_decimal_places)));

        // set adapter
        ArrayAdapter<CharSequence> adapterStartCurrency = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencyRateProvider.getCurrencies());
        adapterStartCurrency.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStartCurrency.setAdapter(adapterStartCurrency);
        if(startCurrency != null) {
            int posStartCurrency = adapterStartCurrency.getPosition(startCurrency);
            if(posStartCurrency >= 0)
                spinnerStartCurrency.setSelection(posStartCurrency);
        }

        ArrayAdapter<CharSequence> adapterTargetCurrency = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencyRateProvider.getCurrencies());
        adapterTargetCurrency.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetCurrency.setAdapter(adapterTargetCurrency);
        if(targetCurrency != null) {
            int posTargetCurrency = adapterTargetCurrency.getPosition(targetCurrency);
            if(posTargetCurrency >= 0)
                spinnerTargetCurrency.setSelection(posTargetCurrency);
        }

        refreshGUI(inputAmount);
    }

    private void refreshGUI(final String startAmount) {
        Log.v(TAG, "refreshGUI");
        tvTimestamp.setText(tvDate.concat(currencyRateProvider.getDate(res.getString(R.string.rate_date_format))));
        if(startAmount!=null && !startAmount.isEmpty())
            etStartAmount.setText(startAmount);
        if(pDialog!=null) {
            pDialog.dismiss();
        }
    }

    private void currencyChanged() {
        if (selectedStartCurrency != null && selectedTargetCurrency != null) {
            final String startAmount = etStartAmount.getText().toString();
            final String targetAmount = etTargetAmount.getText().toString();
            if (!startAmount.isEmpty()) {
                setText(etTargetAmount, selectedStartCurrency, selectedTargetCurrency, etStartAmount.getText().toString());
            } else if (!targetAmount.isEmpty()) {
                setText(etStartAmount, selectedTargetCurrency, selectedStartCurrency, etTargetAmount.getText().toString());
            }
        }
    }

    private void setText(EditText etWidget, String startCurrency, String targetCurrency, String amount) {
        if (setTextFlag) {
            setTextFlag = false;
        } else {
            if (startCurrency != null || targetCurrency != null) {
                setTextFlag = true;
                String result = calculateCurrency(startCurrency, targetCurrency, amount);
                if (result.isEmpty()) {
                    TextKeyListener.clear(etWidget.getText());
                } else {
                    etWidget.setText(result);
                }
            }
        }
    }

    private String calculateCurrency(String startCurrency, String targetCurrency, String amount) {
        String result = "";
        if (amount.length() > 0 && CurrencyConverterUtil.isNumeric(amount)) {
            if (startCurrency.compareTo(targetCurrency) == 0) {
                result = CurrencyConverterUtil.convertOtherCurrency(amount, 1, 1, scale);
            } else if (startCurrency.compareTo(referencedCurrency) == 0 && targetCurrency.compareTo(referencedCurrency) != 0) {
                result = CurrencyConverterUtil.convertEuroCurrency(amount, CurrencyConverterUtil.Type.EURO_TO_OTHER, currencyRateProvider.getRate(CurrencyEntryUtil.parseCurrencyFromString(targetCurrency)),scale);
            } else if (startCurrency.compareTo(referencedCurrency) != 0 && targetCurrency.compareTo(referencedCurrency) != 0) {
                result = CurrencyConverterUtil.convertOtherCurrency(amount, currencyRateProvider.getRate(CurrencyEntryUtil.parseCurrencyFromString(startCurrency)), currencyRateProvider.getRate(CurrencyEntryUtil.parseCurrencyFromString(targetCurrency)),scale);
            } else if (startCurrency.compareTo(referencedCurrency) != 0 && targetCurrency.compareTo(referencedCurrency) == 0) {
                result = CurrencyConverterUtil.convertEuroCurrency(amount, CurrencyConverterUtil.Type.OTHER_TO_EURO, currencyRateProvider.getRate(CurrencyEntryUtil.parseCurrencyFromString(startCurrency)),scale);
            }
        }
        return result;
    }

    private void updateCurrencyRates(LoadManager.LoaderType type) {
        try {
            boolean updateNeeded = currencyRateProvider.updateRates(type);
            if(updateNeeded == false) {
                toastRatesUpToDate.show();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            if(e instanceof NetworkConnectionException) {
                toastError.show();
            } else {
                finish();
            }
        }
    }
    // ####################################### INNER CLASS #######################################
    private class InitTask extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] params) {
            // instantiate database
            CurrencyDatabaseHelper dbHelper = CurrencyDatabaseHelper.getInstance(ConverterActivity.this);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            long count = DatabaseUtils.queryNumEntries(db, CurrencyRatesTbl.TABLE_NAME);
            InputStream defaultCurrencyXml = getResources().openRawResource(R.raw.currency_rates);
            final String currencyUrl = getResources().getString(R.string.currency_url_floatrates);
            currencyRateProvider = new CurrencyRateProviderImpl(referencedCurrency, db, defaultCurrencyXml, currencyUrl);
            LoadManager.LoaderType type;
            if(count == 0) { // load rates from xml file
                type = LoadManager.LoaderType.INIT;
            } else { // load rates from sqlite database
                type = LoadManager.LoaderType.DATABASE;
            }
            updateCurrencyRates(type);
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            postInit();
        }
    }

    private class UpdateTask extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] params) {
            Log.v(TAG, "Start update rates...");
            if(params.length > 0) {
                LoadManager.LoaderType type = (LoadManager.LoaderType)params[0];
                updateCurrencyRates(type);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            Log.v(TAG, "End update rates");
            super.onPostExecute(o);
            refreshGUI(etStartAmount.getText().toString());
        }
    }
}

