package de.medieninf.mobcomp.currencyconverter.persistence;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import de.medieninf.mobcomp.currencyconverter.entities.CurrencyRates;
import de.medieninf.mobcomp.currencyconverter.persistence.interfaces.LoadManager;
import de.medieninf.mobcomp.currencyconverter.util.StreamUtil;

/**
 * Created by bchristiani on 04.05.2015.
 */
public class FileLoadManager extends LoadManager{

    private static final String TAG = FileLoadManager.class.getSimpleName();
    private byte[] fileByteArray;

    public FileLoadManager(LoaderType type, InputStream in) {
        super(type);
        try {
            this.fileByteArray  = StreamUtil.toByteArray(in);
        } catch (Exception e) {
            Log.e(TAG, "Parsing Stream to ByteAray failed");
            e.printStackTrace();
        }
    }

    @Override
    public CurrencyRates load(LoaderType type) throws Exception{
        if(type == this.type) {
            CurrencyRates cr;
            cr = this.consumer.parse(new ByteArrayInputStream(fileByteArray));
            return cr;
        } else {
            return this.lmSuccessor.load(type);
        }
    }
}
