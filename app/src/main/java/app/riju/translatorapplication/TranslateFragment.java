package app.riju.translatorapplication;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.material.textfield.TextInputEditText;
import com.google.errorprone.annotations.ForOverride;

import java.util.List;

public class TranslateFragment extends Fragment {

    public TranslateFragment() {
        // Required empty public constructor
    }

    public static TranslateFragment newInstance() {
        TranslateFragment fragment = new TranslateFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @NonNull Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        super.onViewCreated(view, savedInstanceState);
        final Button switchButton = view.findViewById(R.id.buttonSwitchLang);
        final ToggleButton sourceSyncButton = view.findViewById(R.id.buttonSyncSource);
        final ToggleButton targetSyncButton = view.findViewById(R.id.buttonSyncTarget);
        final TextInputEditText srcTextView = view.findViewById(R.id.sourceText);
        final TextView targetTextView = view.findViewById(R.id.targetText);
        final TextView downloadModelsTextView = view.findViewById(R.id.downloadModels);
        final Spinner targetLangSelector = view.findViewById(R.id.targetLangSelector);
        final Spinner sourceLangSelector = view.findViewById(R.id.sourceLangSelector);

        final TranslateViewModel viewModel = ViewModelProviders.of(this).get(TranslateViewModel.class);

        final ArrayAdapter<TranslateViewModel.Language> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, viewModel.getAvailableLanguages());
        sourceLangSelector.setAdapter(adapter);
        targetLangSelector.setAdapter(adapter);

        sourceLangSelector.setSelected(adapter.getPosition(new TranslateViewModel.Language("en")));
        targetLangSelector.setSelected(adapter.getPosition(new TranslateViewModel.Language("es")));
        sourceLangSelector.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                setProgressText(targetTextView);
                viewModel.sourceLang.setValue(adapter.getItem(i));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                targetTextView.setText("");
            }
        });

        targetLangSelector.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int i, long l) {
                setProgressText(targetTextView);
                viewModel.targetLang.setValue(adapter.getItem(i));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                targetTextView.setText("");
            }

        });

        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setProgressText(targetTextView);
                int sourceLangPosition = sourceLangSelector.getSelectedItemPosition();
                sourceLangSelector.setSelection(targetLangSelector.getSelectedItemPosition());
                targetLangSelector.setSelection(sourceLangPosition);
            }
        });
        sourceSyncButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean b) {
                TranslateViewModel.Language language = adapter.getItem(sourceLangSelector.getSelectedItemPosition());
                if (b) {
                    viewModel.deleteLanguage(language);
                } else {
                    viewModel.deleteLanguage(language);
                }
            }
        });


        targetSyncButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                TranslateViewModel.Language language = adapter.getItem(targetLangSelector.getSelectedItemPosition());
                if (b) {
                    viewModel.downloadLanguage(language);
                } else {
                    viewModel.deleteLanguage(language);
                }
            }
        });
        srcTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                setProgressText(targetTextView);
                viewModel.sourceText.postValue(s.toString());
            }
        });
        viewModel.translatedText.observe(getViewLifecycleOwner(), new Observer<TranslateViewModel.ResultOrError>() {
            @Override
            public void onChanged(TranslateViewModel.ResultOrError resultOrError) {
                if (resultOrError.error != null) {
                    srcTextView.setError(resultOrError.error.getLocalizedMessage());
                } else {
                    targetTextView.setText(resultOrError.result);
                }

            }
        });


        viewModel.avaliableModels.observe(getViewLifecycleOwner(), new Observer<List<String>>() {
            @Override
            public void onChanged(List<String> translateRemoteModels) {
                String output = getContext().getString(R.string.downloaded_model_label, translateRemoteModels);
                downloadModelsTextView.setText(output);
                sourceSyncButton.setChecked(translateRemoteModels.contains(
                        adapter.getItem(sourceLangSelector.getSelectedItemPosition()).getCode()
                ));
                targetSyncButton.setChecked(translateRemoteModels.contains(
                        adapter.getItem(targetLangSelector.getSelectedItemPosition()).getCode()
                ));
            }
        });

    }

    private void setProgressText(TextView tv){
        tv.setText(getContext().getString(R.string.translate_progress));
    }
}