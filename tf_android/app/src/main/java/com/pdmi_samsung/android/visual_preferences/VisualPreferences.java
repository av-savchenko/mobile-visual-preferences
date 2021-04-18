package com.pdmi_samsung.android.visual_preferences;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.*;
import android.widget.*;


import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.*;
import com.github.mikephil.charting.components.*;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by avsavchenko.
 */
public class VisualPreferences extends Fragment implements OnChartValueSelectedListener{
    /** Tag for the {@link Log}. */
    private static final String TAG = "VisualPreferences";

    protected MainActivity mainActivity;
    protected HorizontalBarChart chart=null;

    //protected TextView infoText;
    private Spinner viewOptionsSpinner;
    protected static int viewOptionsSelected=0;

    protected int color, categoryPosition;
    private boolean isDemography=false;
    private Button mapsButton, backButton;


    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_preferences, container, false);
    }

    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        color=getArguments().getInt("color",Color.BLACK);
        categoryPosition=getArguments().getInt("position",0);
        String title =getArguments().getString("title","");

        mainActivity=(MainActivity)getActivity();

        isDemography=categoryPosition==getResources().getStringArray(R.array.category_list).length-1;

        /*infoText=(TextView)view.findViewById(R.id.info_text);
        infoText.setVisibility(isDemography?View.VISIBLE:View.GONE);*/
        viewOptionsSpinner = (Spinner)view.findViewById(R.id.view_options_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                R.layout.spinner_item, getResources().getStringArray(R.array.view_options_list));
        viewOptionsSpinner.setAdapter(adapter);


        TextView titleText=(TextView)view.findViewById(R.id.title_text);
        titleText.setText(title);

        chart = (HorizontalBarChart) view.findViewById(R.id.rating_chart);
        chart.setPinchZoom(false);
        chart.setDrawValueAboveBar(!isDemography);
        Description descr=new Description();
        descr.setText("");
        chart.setDescription(descr);
        XAxis xAxis = chart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setEnabled(true);
        xAxis.setDrawAxisLine(false);

        YAxis yRight = chart.getAxisRight();
        yRight.setDrawAxisLine(true);
        yRight.setDrawGridLines(false);
        yRight.setEnabled(false);

        chart.getAxisLeft().setAxisMinimum(0);

        mapsButton = (Button) view.findViewById(R.id.mapButton);
        mapsButton.setVisibility((!isDemography && getFragmentManager().getBackStackEntryCount()>0)?View.VISIBLE:View.GONE);

        backButton=(Button)view.findViewById(R.id.back_hl_prefs_button);
        backButton.setVisibility((getFragmentManager().getBackStackEntryCount()>0)?View.VISIBLE:View.GONE);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fm = getFragmentManager();
                if(fm.getBackStackEntryCount() > 0) {
                    Log.d(TAG, "popping backstack");
                    fm.popBackStackImmediate();
                }
            }
        });

        chart.setOnChartValueSelectedListener(this);

        if(isDemography) {
            viewOptionsSpinner.setVisibility(View.GONE);
            updateChart();
        }
        else {
            viewOptionsSpinner.setVisibility(View.VISIBLE);
            viewOptionsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,
                                           View itemSelected, int selectedItemPosition, long selectedId) {
                    viewOptionsSelected = selectedItemPosition;
                    updateChart();
                }

                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            viewOptionsSpinner.setSelection(viewOptionsSelected);
        }
    }
    protected List<Map<String,Map<String, Set<String>>>> getCategoriesHistograms(){
        int pos=viewOptionsSpinner.getSelectedItemPosition();
        return mainActivity.getCategoriesHistograms(pos!=1);
    }

    @Override
    public void onValueSelected(Entry entry, Highlight highlight) {
        BarEntry barEntry=(BarEntry)entry;
        IAxisValueFormatter formatter=chart.getXAxis().getValueFormatter();
        if(formatter!=null) {
            String category=formatter.getFormattedValue(entry.getX(), null);
            //Toast.makeText(getActivity(), category + " stack=" + highlight.getStackIndex(), Toast.LENGTH_SHORT).show();
            if(mainActivity==null)
                return;
            FragmentManager fm = getFragmentManager();

            List<Map<String,Map<String,Set<String>>>> categoriesHistograms=getCategoriesHistograms();
            Map<String,Set<String>> fileLists=null;
            if(categoryPosition<categoriesHistograms.size()){
                Map<String,Map<String,Set<String>>> cat_files=categoriesHistograms.get(categoryPosition);
                if(cat_files.containsKey(category)){
                    fileLists=cat_files.get(category);
                }
            }
            else{
                int gender=highlight.getStackIndex();
                fileLists=mainActivity.getDemographyHistogram().get(gender).get((int)(entry.getX()));
            }
            if(fileLists!=null && !fileLists.isEmpty()) {
                    Photos photosFragment = new Photos();
                    Bundle args = new Bundle();
                    String[] titles=new String[fileLists.size()];
                    int i=0;
                    for(Map.Entry<String,Set<String>> category2fileList:fileLists.entrySet()){
                        titles[i]=category2fileList.getKey();
                        if(Character.isDigit(titles[i].charAt(0)))
                            titles[i]=String.valueOf(i+1);
                        args.putStringArrayList(titles[i], new ArrayList<String>(category2fileList.getValue()));
                        ++i;
                    }
                    args.putStringArray("photosTaken", titles);
                    photosFragment.setArguments(args);
                    FragmentTransaction fragmentTransaction = fm.beginTransaction();
                    fragmentTransaction.replace(R.id.fragment_switch, photosFragment);
                    fragmentTransaction.addToBackStack(null);
                    fragmentTransaction.commit();
                }

        }
    }

    @Override
    public void onNothingSelected() {
        //Toast.makeText(getActivity(),"Nothing selected",Toast.LENGTH_SHORT).show();
    }

    public void updateChart(){
        if(mainActivity!=null) {
            if (isDemography) {
                updateDemographyChart();
            } else {
                List<Map<String,Map<String, Set<String>>>> categoriesHistograms=getCategoriesHistograms();
                Map<String,Map<String,Set<String>>> histo=null;
                if (categoryPosition<categoriesHistograms.size()) {
                    histo=categoriesHistograms.get(categoryPosition);
                }
                if(histo!=null && !histo.isEmpty())
                    updateCategoryChart(histo);
                else
                    backButton.performClick();
            }
        }
    }

    protected int getFilesCount(Map<String, Set<String>> id2Files){
        int count = 0;
        for (Set<String> filenames : id2Files.values())
            count += filenames.size();
        return count;
    }
    private void updateCategoryChart(Map<String,Map<String,Set<String>>> histo){
        //infoText.setText("");

        ArrayList<Map.Entry<String,Map<String,Set<String>>>> sortedHisto = new ArrayList<>(histo.entrySet());
        Collections.sort(sortedHisto, new Comparator<Map.Entry<String, Map<String, Set<String>>>>() {
            @Override
            public int compare(Map.Entry<String, Map<String, Set<String>>> kvEntry, Map.Entry<String, Map<String, Set<String>>> t1) {
                return getFilesCount(t1.getValue())-getFilesCount(kvEntry.getValue());
            }
        });

        //Map<String,Set<String>> sortedHisto=sortByValueSize(histo);
        final ArrayList<String> xLabel = new ArrayList<>();
        final List<BarEntry> entries = new ArrayList<BarEntry>();
        int index=0;
        int maxCount=15;
        List<String> keys=new ArrayList<>();
        for (Map.Entry<String,Map<String,Set<String>>> entry : sortedHisto) {
            keys.add(entry.getKey());
            if(keys.size()>maxCount)
                break;
        }
        Collections.reverse(keys);
        for(String key : keys){
            xLabel.add(key);
            int value=(int)Math.round(getFilesCount(histo.get(key)));
            entries.add(new BarEntry(index, value));
            ++index;

            if(index>maxCount)
                break;
        }
        if(!entries.isEmpty())
            chart.getAxisLeft().setAxisMaximum(entries.get(entries.size()-1).getY()+2);

        XAxis xAxis = chart.getXAxis();
        xAxis.setLabelCount(xLabel.size());
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                //value=-value;
                if (value>=0 && value<xLabel.size())
                    return xLabel.get((int)value);
                else
                    return "";

            }
        });

        BarDataSet barDataSet = new BarDataSet(entries, "");
        barDataSet.setColor(color);

        BarData data = new BarData(barDataSet);
        data.setBarWidth(0.7f*xLabel.size()/maxCount);
        data.setValueFormatter(new IValueFormatter(){

            @Override
            public String getFormattedValue(float v, Entry entry, int i, ViewPortHandler viewPortHandler) {
                return "" + ((int) v);
            }
        });
        chart.setData(data);
        chart.getLegend().setEnabled(false);
        chart.invalidate();

        mapsButton.setOnClickListener(but -> {
                try{
                    Intent intent = new Intent(mainActivity, MapsActivity.class);
                    List topCategories = sortedHisto
                            .stream()
                            .limit(5)
                            .map(o -> String.format("%s:%s", o.getKey(), getFilesCount(o.getValue())))
                            .collect(Collectors.toList());
                    String message = TextUtils.join("\n", topCategories);

                    intent.putExtra("query", message);
                    startActivity(intent);
                } catch (Exception ex){
                    Log.e("Maps", "Maps launch error", ex);
                }
            });
    }

    private void updateDemographyChart(){
        final List<String> xLabel=new ArrayList<>();
        for(int i = 1; i<MainActivity.MAX_AGES.length; ++i)
            xLabel.add(MainActivity.getAgeRangeDescription(i));
        XAxis xAxis = chart.getXAxis();
        xAxis.setLabelCount(xLabel.size());
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                if (value>=0 && value<xLabel.size())
                    return xLabel.get((int)value);
                else
                    return "";
            }
        });

        int[] colors={Color.CYAN,Color.MAGENTA};
        int maxHistoVal=0, totalCount=0;
        final List<BarEntry> entries = new ArrayList<BarEntry>();

        List<List<Map<String,Set<String>>>> demographyHisto = mainActivity.getDemographyHistogram();
        for(int i = 1; i<MainActivity.MAX_AGES.length; ++i){
            float[] values=new float[MainActivity.NUM_GENDERS];
            int sumVal=0;
            for(int gender=0;gender<MainActivity.NUM_GENDERS;++gender) {
                int val=demographyHisto.get(gender).get(i-1).size();
                //Log.i(TAG,"!!!! Gender "+maleScore+" i="+i+" ("+MAX_AGES[i]+") val="+val);
                values[gender]=val;
                sumVal+=val;
                if(val>0) {
                    totalCount += val;
                }
            }
            if (maxHistoVal < sumVal)
                maxHistoVal = sumVal;
            entries.add(new BarEntry(i - 1, values));
        }

        chart.getAxisLeft().setAxisMaximum(maxHistoVal + 1);

        BarDataSet barDataSet = new BarDataSet(entries, "");
        barDataSet.setColors(colors);
        barDataSet.setStackLabels(new String[]{"Male","Female"});
        BarData data = new BarData(barDataSet);
        data.setBarWidth(0.5f);

        data.setValueFormatter(new IValueFormatter() {
            @Override
            public String getFormattedValue(float v, Entry entry, int i, ViewPortHandler viewPortHandler) {
                int iV=(int)v;
                return iV>0?"" + iV:"";
            }
        });

        chart.setData(data);
        //chart.groupBars(0, groupSpace, barSpace);
        chart.getLegend().setEnabled(true);
        chart.setFitBars(true);
        chart.invalidate();

        //infoText.setText(String.format("Sociality (number of closed persons): %d",totalCount));
    }

    private static <K, V extends Comparable<? super V>> Map<K, Set<V>> sortByValueSize(Map<K, Set<V>> map) {
        ArrayList<Map.Entry<K, Set<V>>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, Set<V>>>() {
            @Override
            public int compare(Map.Entry<K, Set<V>> kvEntry, Map.Entry<K, Set<V>> t1) {
                return t1.getValue().size()-kvEntry.getValue().size();
            }
        });

        Map<K, Set<V>> result = new LinkedHashMap<>();
        for (Map.Entry<K, Set<V>> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }
}