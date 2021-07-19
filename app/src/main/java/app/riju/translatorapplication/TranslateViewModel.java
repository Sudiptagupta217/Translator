package app.riju.translatorapplication;

import android.app.Application;
import android.media.MediaPlayer;
import android.util.LruCache;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import okhttp3.internal.http.StreamAllocation;

public class TranslateViewModel extends AndroidViewModel {
    private static final int NUM_TRANSLATORS = 3;

    private final RemoteModelManager modelManager;
    private final LruCache<TranslatorOptions, Translator> translator =
            new LruCache<TranslatorOptions, Translator>(NUM_TRANSLATORS) {

                @Override
                protected Translator create(TranslatorOptions key) {
                    return Translation.getClient(key);
                }

                @Override
                protected void entryRemoved(boolean evicted, TranslatorOptions key, Translator oldValue, Translator newValue) {
                    oldValue.close();
                }
            };
    MutableLiveData<TranslateLanguage.Language> sourceLang = new MutableLiveData<>();
    MutableLiveData<TranslateLanguage.Language> targetLang = new MutableLiveData<>();
    MutableLiveData<String> sourceText = new MutableLiveData<>();
    MediatorLiveData<ResultOrError> translatedText = new MediatorLiveData<>();
    MutableLiveData<List<String>> avaliableModels =
            new MutableLiveData<>();

    public TranslateViewModel(@NonNull Application application) {
        super(application);
        modelManager = RemoteModelManager.getInstance();

        final OnCompleteListener<String> processTrnslation = new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (task.isSuccessful()) {
                    translatedText.setValue(new ResultOrError(task.getResult(), null));
                } else {
                    translatedText.setValue(new ResultOrError(null, task.getException()));
                }
            }

//            fetchDownloadModel();
        };
        translatedText.addSource(sourceText, new Observer<TranslateLanguage.Language>() {
            @Override
            public void onChanged(String s) {
                translate().addOnCompleteListener(processTrnslation);
            }
        });

        Observer<Language> languageObserver = new Observer<Language>() {
            @Override
            public void onChanged(Language language) {
                translate().addOnCompleteListener(processTrnslation);
            }
        };
        translatedText.addSource(sourceLang, languageObserver);
        translatedText.addSource(targetLang,languageObserver);

        fetchDownloadModel();


    }

    List<Language> getAvailableLanguages(){
        List<Language> languages=new ArrayList<>();
        List<String> languageIds= TranslateLanguage.getAllLanguages();
        for (String languageId: languageIds){
            languages.add(
                    new Language(TranslateLanguage.fromLanguageTag(languageId))
            );
        }
        return languages;
    }
    private TranslateRemoteModel  getModel(String languageCode){
        return new TranslateRemoteModel().Builder(languageCode).build();
    }

    void downloadLanguage(Language language){
        TranslateRemoteModel model = getModel(TranslateLanguage.fromLanguageTag(language.getCode()));
        modelManager.download(model, new DownloadConditions().Builder().build())
                .addOnSuccessListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        fetchDownloadModel();
                    }
                });
    }
    void deleteLanguage(Language language){
        TranslateRemoteModel model = getModel(TranslateLanguage.fromLanguageTag(language.getCode()));
        modelManager.deleteDownloadedModel(model).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                fetchDownloadModel();
            }
        });
    }

    public Task<String> translate(){
        final String text = sourceText.getValue();
        final Language source = (Language) sourceLang.getValue();
        final Language target = (Language) targetLang.getValue();
        if (source == null || target == null || text == null ||text.isEmpty()) {
            return Tasks.forResult("");
        }
        String sourceLangCode = TranslateLanguage.fromLanguageTag(source.getCode());
        String targetLangCode= TranslateLanguage.fromLanguageTag(target.getCode());
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(targetLangCode)
                .build();

        return  translator.get(options).downloadModelIfNeeded().continueWithTask(
                new Continuation<Void, Task<String>>() {
                    @Override
                    public Task<String> then(@NonNull Task<Void> task) throws Exception {
                        if (task.isSuccessful()){
                            return translator.get(options).translate(text);
                        }
                        else {
                            Exception e = task.getException();
                            if (e== null){
                                e= new Exception(getApplication().getString(R.string.common_google_play_services_unknown_issue));
                            }
                            return Tasks.forException(e);
                        }
                    }
                });
    }
    private void fetchDownloadModel(){
        modelManager.getDownloadedModels(TranslateRemoteModel.class).addOnSuccessListener(new OnSuccessListener<Set<TranslateRemoteModel>>() {
            @Override
            public void onSuccess(Set<TranslateRemoteModel> translateRemoteModels) {
                List<String> modelCodes= new ArrayList<>(translateRemoteModels.size());
                for (TranslateRemoteModel model : translateRemoteModels){
                    modelCodes.add(model.getLanguage());
                }
                Collections.sort(modelCodes);
                avaliableModels.setValue(modelCodes);
            }
        });
    }

    static class ResultOrError {
        final @Nullable
        String result;
        final @Nullable
        Exception error;

        public ResultOrError(@Nullable String result, @Nullable Exception error) {
            this.result = result;
            this.error = error;
        }
    }

    static class Language implements Comparable<TranslateLanguage.Language> {
        private String code;

        Language(String code) {
            this.code = code;
        }

        String getDisplayName() {
            return new Locale(code).getDisplayName();
        }

        String getCode() {
            return code;
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Language)) {
                return false;
            }
            Language otherLang = (Language) o;
            return otherLang.code.equals(code);
        }

        @Override
        public String toString() {
            return code + " - " + getDisplayName();
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }

     @Override
        public int compareTo(@NonNull Language o) {
            return this.getDisplayName().compareTo(o.getDisplayName());
        }

    }
    protected void onCleared(){
        super.onCleared();
        translator.evictAll();
    }
}
