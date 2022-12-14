package com.arcsoft.arcfacedemo.ui.viewmodel;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arcsoft.arcfacedemo.ArcFaceApplication;
import com.arcsoft.arcfacedemo.R;
import com.arcsoft.arcfacedemo.facedb.entity.FaceEntity;
import com.arcsoft.arcfacedemo.faceserver.FaceServer;
import com.arcsoft.arcfacedemo.ui.model.CompareResult;
import com.arcsoft.arcfacedemo.ui.model.PreviewConfig;
import com.arcsoft.arcfacedemo.util.ConfigUtil;
import com.arcsoft.arcfacedemo.util.FaceRectTransformer;
import com.arcsoft.arcfacedemo.util.FileUtil;
import com.arcsoft.arcfacedemo.util.debug.DebugInfoCallback;
import com.arcsoft.arcfacedemo.util.debug.DebugInfoDumper;
import com.arcsoft.arcfacedemo.util.debug.DumpConfig;
import com.arcsoft.arcfacedemo.util.debug.face.DebugFaceHelper;
import com.arcsoft.arcfacedemo.util.face.RecognizeCallback;
import com.arcsoft.arcfacedemo.util.face.constants.LivenessType;
import com.arcsoft.arcfacedemo.util.face.constants.RecognizeColor;
import com.arcsoft.arcfacedemo.util.face.constants.RequestFeatureStatus;
import com.arcsoft.arcfacedemo.util.face.model.FacePreviewInfo;
import com.arcsoft.arcfacedemo.util.face.model.RecognizeConfiguration;
import com.arcsoft.arcfacedemo.widget.FaceRectView;
import com.arcsoft.face.AgeInfo;
import com.arcsoft.face.FaceAttributeParam;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.GenderInfo;
import com.arcsoft.face.LivenessInfo;
import com.arcsoft.face.LivenessParam;
import com.arcsoft.face.enums.DetectFaceOrientPriority;
import com.arcsoft.face.enums.DetectMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class RecognizeDebugViewModel extends ViewModel implements RecognizeCallback, DebugInfoCallback {

    /**
     * ??????????????????????????????????????????
     */
    public enum EventType {
        /**
         * ????????????
         */
        INSERTED,
        /**
         * ????????????
         */
        REMOVED
    }

    public static class FaceItemEvent {
        private int index;
        private EventType eventType;

        public FaceItemEvent(int index, EventType eventType) {
            this.index = index;
            this.eventType = eventType;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public EventType getEventType() {
            return eventType;
        }

        public void setEventType(EventType eventType) {
            this.eventType = eventType;
        }
    }

    private static final String TAG = "RecognizeViewModel";

    private static final int MAX_DETECT_NUM = 10;
    /**
     * ????????????????????????
     */
    private Camera.Size previewSize;
    /**
     * ????????????RecyclerView???????????????
     */
    private MutableLiveData<List<CompareResult>> compareResultList;
    private MutableLiveData<String> noticeLiveData = new MutableLiveData<>();
    private MutableLiveData<String> recognizeNoticeLiveData = new MutableLiveData<>();
    private MutableLiveData<FaceItemEvent> faceItemEventMutableLiveData = new MutableLiveData<>();

    // ?????????????????????????????????
    private MutableLiveData<Integer> ftInitCode = new MutableLiveData<>();
    private MutableLiveData<Integer> frInitCode = new MutableLiveData<>();
    private MutableLiveData<Integer> flInitCode = new MutableLiveData<>();

    /**
     * ???????????????????????????????????????????????????????????????????????????
     */
    private DebugFaceHelper faceHelper;
    /**
     * VIDEO???????????????????????????????????????????????????????????????????????????
     */
    private FaceEngine ftEngine;
    /**
     * ???????????????????????????
     */
    private FaceEngine frEngine;
    /**
     * IMAGE????????????????????????????????????????????????????????????
     */
    private FaceEngine flEngine;

    private PreviewConfig previewConfig;

    private MutableLiveData<RecognizeConfiguration> recognizeConfiguration = new MutableLiveData<>();

    /**
     * ??????ir??????????????????????????????faceData
     */
    private boolean needUpdateFaceData;
    /**
     * ?????????????????????????????????
     */
    private LivenessType livenessType;

    /**
     * IR????????????
     */
    private byte[] irNV21 = null;

    /**
     * dump??????
     */
    private DumpConfig errorDumpConfig;

    private ExecutorService dumpExecutor;

    private String dumpFileDir = null;

    /**
     * ???????????????????????????
     */
    private boolean loadFaceList;

    public void refreshIrPreviewData(byte[] irPreviewData) {
        irNV21 = irPreviewData;
    }

    /**
     * ???????????????????????????????????????
     *
     * @param livenessType ???????????????????????????
     */
    public void setLivenessType(LivenessType livenessType) {
        this.livenessType = livenessType;
    }

    public void setRgbFaceRectTransformer(FaceRectTransformer rgbFaceRectTransformer) {
        faceHelper.setRgbFaceRectTransformer(rgbFaceRectTransformer);
    }

    public void setIrFaceRectTransformer(FaceRectTransformer irFaceRectTransformer) {
        faceHelper.setIrFaceRectTransformer(irFaceRectTransformer);
    }

    public MutableLiveData<List<CompareResult>> getCompareResultList() {
        if (compareResultList == null) {
            compareResultList = new MutableLiveData<>();
            compareResultList.setValue(new ArrayList<>());
        }
        return compareResultList;
    }

    /**
     * ???????????????
     */
    public void init() {
        Context context = ArcFaceApplication.getApplication();
        boolean switchCamera = ConfigUtil.isSwitchCamera(context);
        previewConfig = new PreviewConfig(
                switchCamera ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK,
                switchCamera ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT,
                Integer.parseInt(ConfigUtil.getRgbCameraAdditionalRotation(context)),
                Integer.parseInt(ConfigUtil.getIrCameraAdditionalRotation(context))
        );

        // ?????????????????????????????????????????????
        boolean enableLiveness = !ConfigUtil.getLivenessDetectType(context).equals(context.getString(R.string.value_liveness_type_disable));
        boolean enableFaceQualityDetect = ConfigUtil.isEnableImageQualityDetect(context);
        boolean enableFaceMoveLimit = ConfigUtil.isEnableFaceMoveLimit(context);
        boolean enableFaceSizeLimit = ConfigUtil.isEnableFaceSizeLimit(context);
        RecognizeConfiguration configuration = new RecognizeConfiguration.Builder()
                .enableFaceMoveLimit(enableFaceMoveLimit)
                .enableFaceSizeLimit(enableFaceSizeLimit)
                .enableFaceAreaLimit(ConfigUtil.isRecognizeAreaLimited(context))
                .faceSizeLimit(ConfigUtil.getFaceSizeLimit(context))
                .faceMoveLimit(ConfigUtil.getFaceMoveLimit(context))
                .enableLiveness(enableLiveness)
                .enableImageQuality(enableFaceQualityDetect)
                .maxDetectFaces(ConfigUtil.getRecognizeMaxDetectFaceNum(context))
                .keepMaxFace(ConfigUtil.isKeepMaxFace(context))
                .similarThreshold(ConfigUtil.getRecognizeThreshold(context))
                .imageQualityNoMaskRecognizeThreshold(ConfigUtil.getImageQualityNoMaskRecognizeThreshold(context))
                .imageQualityMaskRecognizeThreshold(ConfigUtil.getImageQualityMaskRecognizeThreshold(context))
                .livenessParam(new LivenessParam(ConfigUtil.getRgbLivenessThreshold(context), ConfigUtil.getIrLivenessThreshold(context), ConfigUtil.getLivenessFqThreshold(context)))
                .build();
        int dualCameraHorizontalOffset = ConfigUtil.getDualCameraHorizontalOffset(context);
        int dualCameraVerticalOffset = ConfigUtil.getDualCameraVerticalOffset(context);
        if (dualCameraHorizontalOffset != 0 || dualCameraVerticalOffset != 0) {
            needUpdateFaceData = true;
        }

        ftEngine = new FaceEngine();
        int ftEngineMask = FaceEngine.ASF_FACE_DETECT | FaceEngine.ASF_MASK_DETECT;
        ftInitCode.postValue(ftEngine.init(context, DetectMode.ASF_DETECT_MODE_VIDEO, ConfigUtil.getFtOrient(context),
                ConfigUtil.getRecognizeMaxDetectFaceNum(context), ftEngineMask));
        FaceAttributeParam attributeParam = new FaceAttributeParam(
                ConfigUtil.getRecognizeEyeOpenThreshold(context), ConfigUtil.getRecognizeMouthCloseThreshold(context),
                ConfigUtil.getRecognizeWearGlassesThreshold(context));
        ftEngine.setFaceAttributeParam(attributeParam);

        frEngine = new FaceEngine();
        int frEngineMask = FaceEngine.ASF_FACE_RECOGNITION;
        if (enableFaceQualityDetect) {
            frEngineMask |= FaceEngine.ASF_IMAGEQUALITY;
        }
        frInitCode.postValue(frEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY,
                10, frEngineMask));
        FaceServer.getInstance().initFaceList(context, frEngine, faceCount -> loadFaceList = true, true);

        // ????????????????????????????????????????????????
        if (enableLiveness) {
            flEngine = new FaceEngine();
            int flEngineMask = (livenessType == LivenessType.RGB ? FaceEngine.ASF_LIVENESS : (FaceEngine.ASF_IR_LIVENESS | FaceEngine.ASF_FACE_DETECT));
            if (needUpdateFaceData) {
                flEngineMask |= FaceEngine.ASF_UPDATE_FACEDATA;
            }
            flInitCode.postValue(flEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE,
                    DetectFaceOrientPriority.ASF_OP_ALL_OUT, 10, flEngineMask));
            LivenessParam livenessParam = new LivenessParam(ConfigUtil.getRgbLivenessThreshold(context), ConfigUtil.getIrLivenessThreshold(context), ConfigUtil.getLivenessFqThreshold(context));
            flEngine.setLivenessParam(livenessParam);
        }
        recognizeConfiguration.setValue(configuration);

        dumpExecutor = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("dumpThread-" + t.getId());
                    return t;
                },
                (r, executor) -> {
                    // ????????????
                    Log.e(TAG, "rejectedExecution: new task deleted");
                });
        dumpExecutor.execute(() -> {
            DebugInfoDumper.getInstance().init();
            dumpFileDir = DebugInfoDumper.getInstance().getCurrentDumpDir();
            getNoticeLiveData().postValue(String.format("crashLogDir:\n%s\n\ndebugLogDir:\n%s", DebugInfoDumper.CRASH_LOG_DIR, dumpFileDir));

            File file = new File(dumpFileDir);
            if (!file.exists() && !file.mkdirs()) {
                return;
            }
            File path = new File(DebugInfoDumper.basicInfoFilePath);
            FileUtil.saveDataToFile(DebugInfoDumper.getBasicInfo().getBytes(), path);

            FileUtil.saveDataToFile("\r\n\r\n".getBytes(), path, true);
            FileUtil.saveDataToFile(recognizeConfiguration.getValue().toString().getBytes(), path, true);

            FileUtil.saveDataToFile("\r\n\r\n".getBytes(), path, true);
            FileUtil.saveDataToFile(String.format("ftOrient:%s", ConfigUtil.getFtOrient(context).toString()).getBytes(), path, true);
            FileUtil.saveDataToFile("\r\n".getBytes(), path, true);
            FileUtil.saveDataToFile(String.format("recognizeScale:%d", ConfigUtil.getRecognizeScale(context)).getBytes(), path, true);
            FileUtil.saveDataToFile("\r\n".getBytes(), path, true);
            FileUtil.saveDataToFile(String.format("livenessType=%s", livenessType == null ? "null" : livenessType.toString()).getBytes(), path, true);
        });

    }

    /**
     * ???????????????faceHelper??????????????????????????????????????????????????????????????????crash
     */
    private void unInit() {
        if (ftEngine != null) {
            synchronized (ftEngine) {
                int ftUnInitCode = ftEngine.unInit();
                Log.i(TAG, "unInitEngine: " + ftUnInitCode);
            }
        }
        if (frEngine != null) {
            synchronized (frEngine) {
                int frUnInitCode = frEngine.unInit();
                Log.i(TAG, "unInitEngine: " + frUnInitCode);
            }
        }
        if (flEngine != null) {
            synchronized (flEngine) {
                int flUnInitCode = flEngine.unInit();
                Log.i(TAG, "unInitEngine: " + flUnInitCode);
            }
        }
    }

    /**
     * ???????????????????????????
     *
     * @param facePreviewInfoList ?????????trackId??????
     */
    public void clearLeftFace(List<FacePreviewInfo> facePreviewInfoList) {
        List<CompareResult> compareResults = compareResultList.getValue();
        if (compareResults != null) {
            for (int i = compareResults.size() - 1; i >= 0; i--) {
                boolean contains = false;
                for (FacePreviewInfo facePreviewInfo : facePreviewInfoList) {
                    if (facePreviewInfo.getTrackId() == compareResults.get(i).getTrackId()) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    compareResults.remove(i);
                    getFaceItemEventMutableLiveData().postValue(new FaceItemEvent(i, EventType.REMOVED));
                }
            }
        }
    }

    /**
     * ????????????
     */
    public void destroy() {
        unInit();
        if (dumpExecutor != null) {
            dumpExecutor.shutdown();
        }
        if (faceHelper != null) {
            ConfigUtil.setTrackedFaceCount(ArcFaceApplication.getApplication().getApplicationContext(), faceHelper.getTrackedFaceCount());
            faceHelper.release();
            faceHelper = null;
        }
        FaceServer.getInstance().release();
    }

    /**
     * ?????????????????????activity????????????????????????????????????
     *
     * @param camera ????????????
     */
    public void onRgbCameraOpened(Camera camera) {
        Camera.Size lastPreviewSize = previewSize;
        previewSize = camera.getParameters().getPreviewSize();
        // ????????????????????????????????????????????????????????????
        initFaceHelper(lastPreviewSize);
    }

    /**
     * ?????????????????????activity????????????????????????????????????
     *
     * @param camera ????????????
     */
    public void onIrCameraOpened(Camera camera) {
        Camera.Size lastPreviewSize = previewSize;
        previewSize = camera.getParameters().getPreviewSize();
        // ????????????????????????????????????????????????????????????
        initFaceHelper(lastPreviewSize);
    }

    private void initFaceHelper(Camera.Size lastPreviewSize) {
        if (faceHelper == null ||
                lastPreviewSize == null ||
                lastPreviewSize.width != previewSize.width || lastPreviewSize.height != previewSize.height) {
            Integer trackedFaceCount = null;
            // ??????????????????????????????
            if (faceHelper != null) {
                trackedFaceCount = faceHelper.getTrackedFaceCount();
                faceHelper.release();
            }
            Context context = ArcFaceApplication.getApplication().getApplicationContext();
            int horizontalOffset = ConfigUtil.getDualCameraHorizontalOffset(context);
            int verticalOffset = ConfigUtil.getDualCameraVerticalOffset(context);
            int maxDetectFaceNum = ConfigUtil.getRecognizeMaxDetectFaceNum(context);
            faceHelper = new DebugFaceHelper.Builder()
                    .ftEngine(ftEngine)
                    .frEngine(frEngine)
                    .flEngine(flEngine)
                    .needUpdateFaceData(needUpdateFaceData)
                    .frQueueSize(maxDetectFaceNum)
                    .flQueueSize(maxDetectFaceNum)
                    .previewSize(previewSize)
                    .recognizeCallback(this)
                    .recognizeConfiguration(recognizeConfiguration.getValue())
                    .trackedFaceCount(trackedFaceCount == null ? ConfigUtil.getTrackedFaceCount(context) : trackedFaceCount)
                    .dualCameraFaceInfoTransformer(faceInfo -> {
                        FaceInfo irFaceInfo = new FaceInfo(faceInfo);
                        irFaceInfo.getRect().offset(horizontalOffset, verticalOffset);
                        return irFaceInfo;
                    })
                    .build();
            faceHelper.setErrorCallback(RecognizeDebugViewModel.this);
            faceHelper.setErrorDumpConfig(errorDumpConfig);
        }
    }

    @Override
    public void onRecognized(CompareResult compareResult, Integer liveness, boolean similarPass) {
        Disposable disposable = Observable.just(true).observeOn(AndroidSchedulers.mainThread()).subscribe(aBoolean -> {
            if (similarPass) {
                boolean isAdded = false;
                List<CompareResult> compareResults = compareResultList.getValue();
                if (compareResults != null && !compareResults.isEmpty()) {
                    for (CompareResult compareResult1 : compareResults) {
                        if (compareResult1.getTrackId() == compareResult.getTrackId()) {
                            isAdded = true;
                            break;
                        }
                    }
                }
                if (!isAdded) {
                    //??????????????????????????????????????????????????? MAX_DETECT_NUM ??????????????????????????????????????????????????????
                    if (compareResults != null && compareResults.size() >= MAX_DETECT_NUM) {
                        compareResults.remove(0);
                        getFaceItemEventMutableLiveData().postValue(new FaceItemEvent(0, EventType.REMOVED));
                    }
                    if (compareResults != null) {
                        compareResults.add(compareResult);
                        getFaceItemEventMutableLiveData().postValue(new FaceItemEvent(compareResults.size() - 1, EventType.INSERTED));
                    }
                }
            }
        });
    }

    @Override
    public void onNoticeChanged(String notice) {
        if (recognizeNoticeLiveData != null) {
            recognizeNoticeLiveData.postValue(notice);
        }
    }

    public MutableLiveData<Integer> getFtInitCode() {
        return ftInitCode;
    }

    public MutableLiveData<Integer> getFrInitCode() {
        return frInitCode;
    }

    public MutableLiveData<Integer> getFlInitCode() {
        return flInitCode;
    }

    public MutableLiveData<FaceItemEvent> getFaceItemEventMutableLiveData() {
        return faceItemEventMutableLiveData;
    }

    /**
     * ????????????????????????????????????
     *
     * @param facePreviewInfoList ????????????
     * @return ????????????
     */
    public List<FaceRectView.DrawInfo> getDrawInfo(List<FacePreviewInfo> facePreviewInfoList, LivenessType livenessType) {
        List<FaceRectView.DrawInfo> drawInfoList = new ArrayList<>();
        for (int i = 0; i < facePreviewInfoList.size(); i++) {
            int trackId = facePreviewInfoList.get(i).getTrackId();
            String name = faceHelper.getName(trackId);
            Integer liveness = faceHelper.getLiveness(trackId);
            Integer recognizeStatus = faceHelper.getRecognizeStatus(trackId);

            // ?????????????????????????????????????????????
            int color = RecognizeColor.COLOR_UNKNOWN;
            if (recognizeStatus != null) {
                if (recognizeStatus == RequestFeatureStatus.FAILED) {
                    color = RecognizeColor.COLOR_FAILED;
                }
                if (recognizeStatus == RequestFeatureStatus.SUCCEED) {
                    color = RecognizeColor.COLOR_SUCCESS;
                }
            }
            if (liveness != null && liveness == LivenessInfo.NOT_ALIVE) {
                color = RecognizeColor.COLOR_FAILED;
            }

            drawInfoList.add(new FaceRectView.DrawInfo(
                    livenessType == LivenessType.RGB ?
                            facePreviewInfoList.get(i).getRgbTransformedRect() :
                            facePreviewInfoList.get(i).getIrTransformedRect(),
                    GenderInfo.UNKNOWN, AgeInfo.UNKNOWN_AGE, liveness == null ? LivenessInfo.UNKNOWN : liveness, color,
                    name == null ? "" : name));
        }
        return drawInfoList;
    }

    /**
     * ?????????????????????????????????
     *
     * @param nv21        ???????????????????????????
     * @param doRecognize ??????????????????
     * @return ??????????????????????????????
     */
    public List<FacePreviewInfo> onPreviewFrame(byte[] nv21, boolean doRecognize) {
        if (faceHelper != null) {
            if (!loadFaceList) {
                return null;
            }
            if (livenessType == LivenessType.IR && irNV21 == null) {
                return null;
            }
            return faceHelper.onPreviewFrame(nv21, irNV21, doRecognize);
        }
        return null;
    }

    /**
     * ?????????????????????????????????View???
     *
     * @param recognizeArea ???????????????
     */
    public void setRecognizeArea(Rect recognizeArea) {
        if (faceHelper != null) {
            faceHelper.setRecognizeArea(recognizeArea);
        }
    }

    public MutableLiveData<RecognizeConfiguration> getRecognizeConfiguration() {
        return recognizeConfiguration;
    }

    public PreviewConfig getPreviewConfig() {
        return previewConfig;
    }

    public Point loadPreviewSize() {
        String[] size = ConfigUtil.getPreviewSize(ArcFaceApplication.getApplication()).split("x");
        return new Point(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
    }

    public void setErrorDumpConfig(DumpConfig errorDumpConfig) {
        this.errorDumpConfig = errorDumpConfig;
    }

    @Override
    public void onNormalErrorOccurred(int errorType, byte[] nv21, String fileName) {
        DebugInfoDumper.saveNormalData(errorType, dumpFileDir, fileName, nv21, dumpExecutor);
    }

    @Override
    public void onCompareFailed(byte[] nv21, String fileName, String recognizeFeatureName,
                                String registerFeatureName, byte[] recognizeFaceFeature, FaceEntity faceEntity) {
        DebugInfoDumper.saveCompareFailedData(dumpFileDir, fileName, nv21, recognizeFeatureName,
                registerFeatureName, recognizeFaceFeature, faceEntity, dumpExecutor);
    }

    @Override
    public void onRecognizePass(String performanceInfo) {
        DebugInfoDumper.savePerformanceData(dumpFileDir, "performance.txt", performanceInfo, dumpExecutor);
    }

    @Override
    public void onSavePerformanceInfo(String performanceInfo) {
        DebugInfoDumper.savePerformanceData(dumpFileDir, "performance.txt", performanceInfo, dumpExecutor);
    }

    public void notifyEnableFaceTrackChanged(boolean enable) {
        errorDumpConfig.setDumpFaceTrackError(enable);
    }

    public MutableLiveData<String> getNoticeLiveData() {
        return noticeLiveData;
    }

    public MutableLiveData<String> getRecognizeNoticeLiveData() {
        return recognizeNoticeLiveData;
    }
}
