package com.ist.rylibrary.base.controller;

import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;
//import com.ist.asr.RRasr;
//import com.ist.asr.RRtts;
import com.ist.asr.RRasr;
import com.ist.rylibrary.base.application.RyApplication;
import com.ist.rylibrary.base.entity.FinalQABean;
import com.ist.rylibrary.base.entity.FinalQASemanticBean;
import com.ist.rylibrary.base.entity.FinalQASemanticSlotsBean;
import com.ist.rylibrary.base.entity.FinalQASemanticSlotsObjBean;
import com.ist.rylibrary.base.entity.FinalQAnswerBean;
import com.ist.rylibrary.base.event.AiuiMessageEvent;
import com.ist.rylibrary.base.listener.BaseAiuiListener;
import com.ist.rylibrary.base.listener.BaseRRasrListener;
import com.ist.rylibrary.base.listener.BaseRRttsListener;
import com.ist.rylibrary.base.listener.specialAiuiListener;
import com.ist.rylibrary.base.listener.RyRRttsListener;
import com.ist.rylibrary.base.listener.VoiceResultListener;
import com.ist.rylibrary.base.service.AiuiService;
import com.ist.rylibrary.base.service.InfraredService;
import com.ist.rylibrary.base.util.ToolUtil;
import com.ist.rylibrary.myfloatwindow.controller.FloatWindowController;
import com.renying.m4.AIUI2;
import com.renying.m4.AiuiObj;
import com.renying.m4.RRtts;
import com.renying.m4.Xunfei;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Created by minyuchun on 2017/3/25.
 * aiui控制类
 */

public class AiuiController {
    private  String TAG="AiuiController";
    /**播放的实例*/
    public AiuiObj aiui;
    /**播放的类型*/
    private String AiuiType;
    /**语音合成对象*/
    private RRasr asr;
    /**语音识别对象*/
    private RRtts tts;
    /**每一次的问答结果类*/
    private FinalQABean finalQABean = null;
    /**屏蔽识别的话之后解析*/
    private String[] ShieldAsk;
    /**aiui监听*/
    private AiuiListener mAiuiListener;
    /**当前的爱意控制器实例*/
    private static AiuiController mAiuiController;
    /**解析问答类*/
    FinalQABean qAnswer = null;
    /**是否允许语音被打断*/
    private boolean isAllowInterrupt = true;
    /**自定义语音合成接口*/
    private RyRRttsListener mRyRRttsListener;
    /**号码达人的接口回调接口*/
    private specialAiuiListener mSpecialAiuiListener;
    /**全局变量**/
    private RyApplication ryApplication;
    /**清除*/
    private String code;
    /**判断是否在业务场景中
     * 当唤醒语音时使用，当为true时 当aiui自动关闭时会主动唤醒 不需要通过呼喊唤醒
     * 当false时,aiui休眠后不唤醒，需要通过在业务程序界面调用 AiuiController.getInstance().post(AiuiService.AIUI_TYPE_OPEN);
     *                                                      或者AiuiController.getInstance().AiuiWakeUp();
     * 来唤醒
     * */
    private boolean isAutoWakeUp = true;
    private boolean isRobotAnswer = true;

    private Handler mHandler;

    public static AiuiController getInstance(){
        if(mAiuiController == null){
            mAiuiController = new AiuiController();
        }
        return mAiuiController;
    }

    public void clear(){
        stopHandle();
        if(mAiuiController!=null){
            mAiuiController = null;
        }
    }

    private AiuiController(){
        mHandler = new Handler();
        ShieldAsk = new String[]{"嗯","嗯。","一","一。","阿","啊","啦","唉","呢","吧","了","哇","呀","吗","哦","哈","哟","么"};
        openHandle();
    }

    public void openHandle(){
        if(mHandler!=null){
            mHandler.postDelayed(mRunnable,30*1000);
        }
    }

    public void stopHandle(){
        if(mHandler!=null){
            mHandler.removeCallbacks(mRunnable);
        }
    }

    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if(isAutoWakeUp() &&
                    !YinDaoController.getInstance().isInYindaoProcess() &&
                    !JiangJieController.getInstance().isInJiangJieProcess()){
                post(AiuiService.AIUI_TYPE_OPEN);
            }
            if(mHandler!=null){
                mHandler.postDelayed(this,30*1000);
            }
        }
    };


    public interface AiuiListener{
        /***
         *  aiui自定义返回
         * @param aiuiType 返回类型
         * @param isCustom  是否自定义
         * @param message  返回的数据
         */
        void AiuiMessage(int aiuiType,boolean isCustom,String message);
    }

    /***
     * 添加监听
     * @param aiuiListener 监听
     */
    public void setAiuiListener(AiuiListener aiuiListener){
        this.mAiuiListener = aiuiListener;
    }

    /**
     * 发送消息
     * @param type aiui类型
     */
    public void post(int type){
        post(type,false,null);
    }

    /**
     * 发送消息
     * @param message  发送的信息
     */
    public void post(String message){
        post(AiuiService.AIUI_TYPE_DEFAULT,false,message);
    }
    /**
     * 发送消息
     * @param isCustom  是否自定义
     * @param message  发送的信息
     */
    public void post(boolean isCustom,String message){
        post(AiuiService.AIUI_TYPE_DEFAULT,isCustom,message);
    }
    /***
     * 发送信息
     * @param type aiui类型
     * @param isCustom 是否自定义
     * @param message 发送的信息
     */
    public void post(int type,boolean isCustom,String message){
        EventBus.getDefault().post(new AiuiMessageEvent(type,isCustom,message));
    }
    /**
     * 回收方法
     * @param type  消息类型
     * @param isCustom  是否自定义
     * @param message  消息内容
     */
    public void recovery(int type,boolean isCustom,String message){
        if(mAiuiListener!=null){
            mAiuiListener.AiuiMessage(type,isCustom,message);
        }
    }
    public FinalQABean getFinalQABean() {
        return finalQABean;
    }

    public void setFinalQABean(FinalQABean finalQABean) {
        this.finalQABean = finalQABean;
    }

    /***
     * 初始化语音合成模块
     */
    public void initRRArs(){
        try{
            RRasr.InitApp(RyApplication.getContext());
            asr = new RRasr();
            asr.SetDefaultAnswer("哎呀，我没听明白呢;贵宾，您说慢点，RR还在学习中呢;没有听清楚呢，您在大声的说一遍");
            BaseRRasrListener mRRasrListener = new BaseRRasrListener();
            asr.InitActivity(RyApplication.getContext(), mRRasrListener, true);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /***
     * 初始化语音识别模块
     */
    public void initRRtts(){
        try{
            tts = new RRtts();
            BaseRRttsListener mRRttsListener = new BaseRRttsListener(tts);
            tts.InitActivity(RyApplication.getContext(), mRRttsListener, "local");
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /**
     * aiui配置觉醒
     */
    public void AiuiWork() {
        try{
            if (aiui == null) {
                aiui = getNewObj();
                BaseAiuiListener mAIUIListener = new BaseAiuiListener(AiuiType);
                if(aiui!=null){
                    RyApplication ryApplication=(RyApplication)RyApplication.getContext().getApplicationContext();
                    ryApplication.setAiuiOpen(true);
                    aiui.SetContext(RyApplication.getContext());
                    aiui.AIUIWork(mAIUIListener, 0, 0, "");
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /***
     * 关闭AIUI
     * **/
    public void AiuiStop(){
        RyApplication ryApplication=(RyApplication)RyApplication.getContext().getApplicationContext();
        if(aiui!=null){
            aiui.AIUIStop();
            ryApplication.setAiuiOpen(false);
        }
    }

    /***
     * 新建aiui实例
     * @return  返回新建的aiui
     */
    private AiuiObj getNewObj() {
            AiuiObj obj = null;
            try{
                AiuiType = AiuiObj.getAiuiType();
                if (AiuiType.equals("aiui")) {
                    obj = new AIUI2();
                }else{
                    obj = new Xunfei();
                }
            }catch (Exception e){
                e.printStackTrace();
        }
        return obj;
    }
    /**
     * AIUI/5麦唤醒，统一调用该函数
     */
    public void AiuiWakeUp() {
        try{
            if (aiui!=null) {
                aiui.WakeUp();
                isAutoWakeUp = true;//默认自动唤醒
                RyApplication.getLog().d("AiuiController aiui唤醒");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /***
     * 识别停止
     */
    public void AiuiSleep(){
        try{
            if (aiui!=null){
                aiui.ResetAiui();//休眠-停止识别
                RyApplication.getLog().d("AiuiController aiui识别停止");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /**停止语音合成*/
    public void stopTTS() {
        if (tts != null) {
            tts.Cancel();
        }
    }
    /**
     * 开始语音合成
     * @param text 播放的语音内容
     */
    public void startTTS(final String text) {
        if (tts != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int result = tts.Start(text);
                    FloatWindowController.getInstance().post(text,null);
                }
            }).start();
        }
    }

    /**
     * 判断 问题是否合理
     * @return  是否是合格的问题
     */
    private synchronized boolean isAskRationality(String ask){
        return !(ask!=null && ask.length()<2);
    }

    /***
     * 　底盘监听回调
     */
    public interface YinDaoListener{
        void YinDaoComplete(boolean isComplete);
    }

    /**
     * 语义分析结果
     * @param jsonObject  传递的识别结果
     */
    public void analysisResult(JSONObject jsonObject){
        try{
            //有语义返回，引导到家时清0
            InfraredService.clearWaitGuideCount();
            //语音识别结果返回
            if(!AiuiType.equals("aiui")){
                qAnswer = analysis5(jsonObject);
            }else{
                qAnswer = analysisAiui(jsonObject);
            }
            if (qAnswer!=null && qAnswer.getText()!=null){
                for (String str: ShieldAsk) {//屏蔽 嗯。 啊。 这些词
                    if(qAnswer.getText().equals(str)){
                        return;
                    }
                }
                RyApplication.getLog().d("SceneController 范爷语义解析后获得的数据 "+qAnswer.toString());
                String asrResultJsonOrCmd=qAnswer.getText();
                RyApplication.getLog().d("用户说的话::"+asrResultJsonOrCmd);
                RyApplication ryApplication=(RyApplication)RyApplication.getContext().getApplicationContext();
                if(ryApplication.getPerson()!=null){
                    RyApplication.getLog().d(
                            "是新用户?"+ryApplication.getPerson().isNewPerson()
                            +"是否已经上传人脸图片给face++？"+SceneController.getInstance().isUseOldQuestion);
                    if(ryApplication.getPerson().isNewPerson()
                            &&!SceneController.getInstance().isUseOldQuestion
                            &&SceneController.getInstance().isAddUserFace){
                        if(asrResultJsonOrCmd.indexOf("好")>-1){
                            SceneController.getInstance().isUseOldQuestion=true;
                            SceneController.getInstance().rrPeople(SceneController.getInstance().lastQAnswer,
                                    SceneController.getInstance().lastIflySemantic);
                        }

                    }
                }
                FloatWindowController.getInstance().post(null,qAnswer.getText());
                if(!specialServiceOrOper(qAnswer)){
                    if(isRobotAnswer){
                        SceneController.getInstance().rrPeople(qAnswer,jsonObject.toString());
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /***
     * 号码达人的处理类
     * @param finalQABean
     * @return  是否属于号码达人区域
     */
    private boolean NumberMan(FinalQABean finalQABean){
        return false;
    }

    //设置机器人是否需要回答
    public void setRobotAnswer(boolean robotAnswer) {
        isRobotAnswer = robotAnswer;
    }

    public boolean isRobotAnswer() {
        return isRobotAnswer;
    }

    //检查service
    private boolean specialServiceOrOper(FinalQABean bean){
        boolean isSure = false;
        if(bean != null && bean.getService()!=null){
            if(bean.getService().equals("号码达人")){
                isSure = NumberService();
            }else if(bean.getSemantic()!=null && bean.getSemantic().getSlots()!=null
                    && bean.getSemantic().getSlots().getOper()!=null ){
                if(bean.getSemantic().getSlots().getOper().trim().equals("口令")
                 || bean.getSemantic().getSlots().getOper().equals("选择")){
                    isSure = cmdOper();
                }

            }
        }
        Log.i("service","是否屏蔽" + isSure);
        return isSure;
    }

    /***
     * 号码达人返回
     * @return 是否是号码达人返回
     */
    private boolean NumberService(){
        boolean isNumberService = false;
        if (getSpecialAiuiListener()!=null){
            if(qAnswer.getSemantic().getSlots().getOper().equals("索引修改")){
                RyApplication.getLog().d("索引修改");
                isNumberService = true;
                code = numberIndexChange();
            }else if(qAnswer.getSemantic().getSlots().getOper().equals("修改")){
                RyApplication.getLog().d("修改");
                isNumberService = true;
                code = numberChange();
            }else if(qAnswer.getSemantic().getSlots().getOper().equals("手机号")){
                RyApplication.getLog().d("手机号");
                isNumberService = true;
                if (qAnswer.getSemantic().getSlots().getCode()!=null){
                    code = qAnswer.getSemantic().getSlots().getCode();
                }
            }else if(qAnswer.getSemantic().getSlots().getOper().equals("年龄")
                    || qAnswer.getSemantic().getSlots().getOper().equals("数字")){
                RyApplication.getLog().d("年龄,数字");
                if (qAnswer.getSemantic().getSlots().getCode()!=null){
                    qAnswer.getSemantic().getSlots().setCode(
                            ToolUtil.getInstance().chineseNumber2Int(
                                    qAnswer.getSemantic().getSlots().getCode()
                            )
                    );
                    String number = qAnswer.getSemantic().getSlots().getCode();
                    if(getSpecialAiuiListener().numberResult(
                            qAnswer.getText(),
                            number,
                            qAnswer.getSemantic().getSlots()
                    )){
                        setSpecialAiuiListener(null);
                    }
                }
                return true;
            }
            if(code!=null){
                if(getSpecialAiuiListener().numberResult(
                        qAnswer.getText(),
                        code,
                        qAnswer.getSemantic().getSlots()
                )){
                    setSpecialAiuiListener(null);
                }
            }
        }
        return isNumberService;
    }

    /***
     * 索引替换号码达人
     * @return 号码
     */
    private String numberIndexChange(){
        String phone = "";
        try{
            if(code!=null
                    && qAnswer.getSemantic().getSlots().getCorrectPart()!=null
                    && qAnswer.getSemantic().getSlots().getWrongPart()!=null
                    && qAnswer.getSemantic().getSlots().getPosRank()!=null){
                //字符转换
                qAnswer.getSemantic().getSlots().setCorrectPart(
                        ToolUtil.getInstance().chineseNumber2Int(
                                qAnswer.getSemantic().getSlots().getCorrectPart()
                        )
                );
                //字符转换
                qAnswer.getSemantic().getSlots().setWrongPart(
                        ToolUtil.getInstance().chineseNumber2Int(
                                qAnswer.getSemantic().getSlots().getWrongPart()
                        )
                );
                //对应自负数量
                if(qAnswer.getSemantic().getSlots().getWrongPart().length()>qAnswer.getSemantic().getSlots().getCorrectPart().length()){
                    qAnswer.getSemantic().getSlots().setWrongPart(
                            qAnswer.getSemantic().getSlots().getWrongPart().substring(
                                    qAnswer.getSemantic().getSlots().getWrongPart().length()-qAnswer.getSemantic().getSlots().getCorrectPart().length()
                            )
                    );
                }
                //索引位置赋值
                int index =0;
                try{
                    index = Integer.parseInt(qAnswer.getSemantic().getSlots().getPosRank());
                }catch (Exception e){
                    e.printStackTrace();
                }
                String[] message = code.split(qAnswer.getSemantic().getSlots().getWrongPart());
                if(index == -1){
                    if(code.endsWith(qAnswer.getSemantic().getSlots().getWrongPart())){
                        index = message.length;
                    }else{
                        index = message.length-1;
                    }
                }
                if(message.length>index){
                    phone = forEachNumber(index,message);
                }else if(message.length==index){
                    if(code.startsWith(qAnswer.getSemantic().getSlots().getWrongPart())
                            && code.endsWith(qAnswer.getSemantic().getSlots().getWrongPart())){
                        phone = forEachNumber(index,message);
                    }else if(code.startsWith(qAnswer.getSemantic().getSlots().getWrongPart())) {
                        phone = code.replaceAll(qAnswer.getSemantic().getSlots().getWrongPart(), qAnswer.getSemantic().getSlots().getCorrectPart());
                    }else if(code.endsWith(qAnswer.getSemantic().getSlots().getWrongPart())){
                        phone = forEachNumber(index,message);
                    }else{
                        phone = code;
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return phone;
    }

    /**
     * 修改 号码达人
     * @return 号码
     */
    private String numberChange(){
        String phone = "";
        try{
            if(code!=null && qAnswer.getSemantic().getSlots().getCorrectPart()!=null
                    && qAnswer.getSemantic().getSlots().getWrongPart()!=null){
                qAnswer.getSemantic().getSlots().setWrongPart(
                        ToolUtil.getInstance().chineseNumber2Int(
                                qAnswer.getSemantic().getSlots().getWrongPart()
                        )
                );
                qAnswer.getSemantic().getSlots().setCorrectPart(
                        ToolUtil.getInstance().chineseNumber2Int(
                                qAnswer.getSemantic().getSlots().getCorrectPart()
                        )
                );
                if(qAnswer.getSemantic().getSlots().getWrongPart().length()>qAnswer.getSemantic().getSlots().getCorrectPart().length()){
                    qAnswer.getSemantic().getSlots().setWrongPart(
                            qAnswer.getSemantic().getSlots().getWrongPart().substring(
                                    qAnswer.getSemantic().getSlots().getWrongPart().length()-qAnswer.getSemantic().getSlots().getCorrectPart().length()
                            )
                    );
                }
                phone = code.replaceAll(qAnswer.getSemantic().getSlots().getWrongPart(),
                        qAnswer.getSemantic().getSlots().getCorrectPart());;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return phone;
    }

    /**
     * 循环赋值号码达人
     * @param index 索引
     * @param message  循环数组
     * @return 号码
     */
    private String forEachNumber(int index,String[] message){
        String phone = "";
        try{
            for (int i=0 ;i<message.length; i++){
                if((index-1) == i){
                    phone = phone + message[i] + qAnswer.getSemantic().getSlots().getCorrectPart();
                }else if(i == (message.length-1)){
                    if(code.endsWith(qAnswer.getSemantic().getSlots().getWrongPart())){
                        phone = phone + message[i]+qAnswer.getSemantic().getSlots().getWrongPart();
                    }else{
                        phone = phone + message[i];
                    }
                }else{
                    phone = phone + message[i] + qAnswer.getSemantic().getSlots().getWrongPart();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return phone;
    }



    /**
     * 口令返回
     * @return 是否是口令返回
     */
    private boolean cmdOper(){
//        RyApplication.getLog().d("Aiui 口令");
        if(getSpecialAiuiListener() != null){
//            RyApplication.getLog().d("Aiui 口令1");
            FinalQASemanticBean semanticBean = qAnswer.getSemantic();
            if(semanticBean != null){
                FinalQASemanticSlotsBean slotsBean = semanticBean.getSlots();
                if(slotsBean != null ){
//                    RyApplication.getLog().d("Aiui 口令3 ");
                    if(slotsBean.getOper().equals("口令") || slotsBean.getOper().equals("选择")){
                        FinalQASemanticSlotsObjBean objBean = slotsBean.getObj();
//                        RyApplication.getLog().d("Aiui 口令4 "+objBean);
                        if(objBean != null){
                            String item = objBean.getItem();
//                            RyApplication.getLog().d("Aiui 口令2 "+item);
                            if(item != null){
//                                RyApplication.getLog().d("Aiui 口令5 ");
                                if(getSpecialAiuiListener().cmdResult(qAnswer.getText(),item,qAnswer.getSemantic().getSlots())){
                                    setSpecialAiuiListener(null);
                                }
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /***
     * aiui 解析
     * @param jsonObject
     */
    public FinalQABean analysisAiui(JSONObject jsonObject){
        //讯飞集成语义转换解析
        try{
            Gson gson = new Gson();
            finalQABean = gson.fromJson(jsonObject.toString(),FinalQABean.class);
        }catch (Exception e){
            e.printStackTrace();
        }
        try{
            if(finalQABean==null){
                finalQABean = new FinalQABean();
                finalQABean.setService(ToolUtil.getInstance().readStringJson(jsonObject,"service"));
                finalQABean.setText(ToolUtil.getInstance().readStringJson(jsonObject,"text"));
                if(jsonObject.has("answer")){
                    JSONObject jsAnswer = jsonObject.getJSONObject("answer");
                    if(jsAnswer!=null){
                        FinalQAnswerBean finalQAnswerBean = new FinalQAnswerBean();
                        finalQAnswerBean.setText(ToolUtil.getInstance().readStringJson(jsAnswer,"text"));
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return finalQABean;
    }

    /***
     * 5麦 解析
     * @param jsonObject
     */
    public FinalQABean analysis5(JSONObject jsonObject){
        Log.i("BaseAiuiListener","5麦开始解析");
        FinalQABean qAnswer = new FinalQABean();
        try{
          ryApplication=(RyApplication) RyApplication.getContext().getApplicationContext();
        }catch (Exception e){
            e.printStackTrace();
        }
        try{
            if(jsonObject.has("text") && jsonObject.has("answer")){//普通答案
                qAnswer.setText(jsonObject.getString("text"));
                JSONObject jsAnswer = jsonObject.getJSONObject("answer");
                FinalQAnswerBean finalQAnswerBean = new FinalQAnswerBean();
                finalQAnswerBean.setText(jsAnswer.getString("text"));
                qAnswer.setAnswer(finalQAnswerBean);//默认的回答
            }else if (jsonObject.has("text") && jsonObject.has("data") && jsonObject.has("service")) {
                qAnswer.setText(jsonObject.getString("text"));
                String service = jsonObject.getString("service");
                JSONObject jsData = jsonObject.getJSONObject("data");
                if (service.equals("weather")) {//天气
                    FinalQAnswerBean finalQAnswerBean = new FinalQAnswerBean();
                    finalQAnswerBean.setText(getWeather(jsData.getJSONArray("numberResult")));
                    qAnswer.setAnswer(finalQAnswerBean);
                } else if (service.equals("music")) {//音乐
                    if(ryApplication!=null){
                        if(ryApplication.isPlayMusic()){
                            Log.i(TAG,"允许播放音乐！");
                            FinalQAnswerBean finalQAnswerBean = new FinalQAnswerBean();
                            finalQAnswerBean.setText(getMusic5(jsData.getJSONArray("numberResult")));
                            qAnswer.setAnswer(finalQAnswerBean);
                        }else{
                            Log.i(TAG,"不允许播放音乐");
                            SceneController.getInstance().rrPeople(qAnswer,jsonObject.toString());
                        }
                    }else{
                        Log.e(TAG,"ryApplication===NULL");
                    }

                }
            }else if(jsonObject.has("text")){
                qAnswer.setText(jsonObject.getString("text"));
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return qAnswer;
    }

    /***
     * 获得普通的答案
     * @param jsAnswer
     * @return
     */
    private String getOrdinary(JSONObject jsAnswer){
        String answer = null;
        try{
            if(jsAnswer.has("text")){
                answer = jsAnswer.getString("text");
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            return answer;
        }
    }
    /**
     * 解析5麦音乐
     * @param jaResult
     * @return
     */
    private String getMusic5(JSONArray jaResult){
        String singer = null,name = null;
        try{
            if(jaResult.length()>0){
                JSONObject jsAnswer = jaResult.getJSONObject(0);
                final String path = jsAnswer.getString("downloadUrl");
                name = jsAnswer.getString("name");
                singer = jsAnswer.getString("singer");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MediaVoiceController.playVoice(path, new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mediaPlayer) {
//                                EventBus.getDefault().post(new AiuiMessageEvent("您是否还需要听其他的歌曲呢？如果需要，请报歌名！"));
                                AiuiController.getInstance().post("您是否还需要听其他的歌曲呢？如果需要，请报歌名！");
                                AiuiController.getInstance().setAllowInterrupt(true);
                            }
                        }, new MediaPlayer.OnErrorListener() {
                            @Override
                            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                                AiuiController.getInstance().setAllowInterrupt(true);
                                return false;
                            }
                        });
                    }
                }).start();
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(singer!=null && name!=null){
                return "请欣赏" + singer + "," + name;
            }else{
                return null;
            }
        }
    }

    /***
     * 获取天气的解析
     * @param jaResult
     * @return
     */
    private String getWeather(JSONArray jaResult){
        String answer = null;
        try{
            if(jaResult.length()>0){
                JSONObject jsWeather = jaResult.getJSONObject(0);
                if(jsWeather.has("weather")){
                    answer = "天气"+jsWeather.getString("weather");
                }
                if(jsWeather.has("airQuality")){
                    answer = answer+";空气质量是"+jsWeather.getString("airQuality");
                }
                if(jsWeather.has("tempRange")){
                    answer = answer+";温度"+jsWeather.getString("tempRange");
                }
                if(jsWeather.has("wind")){
                    answer = answer+";"+jsWeather.getString("wind");
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(answer!=null){
                return answer;
            }else{
                return null;
            }
        }
    }

    public boolean isAllowInterrupt() {
        return isAllowInterrupt;
    }

    public void setAllowInterrupt(boolean allowInterrupt) {
        isAllowInterrupt = allowInterrupt;
    }

    public void setRyRRttsListener(RyRRttsListener listener) throws RuntimeException{
        mRyRRttsListener = listener;
    }

    public void setSpecialAiuiListener(specialAiuiListener numberListener) {
        mSpecialAiuiListener = numberListener;
        code = null;
    }

    public specialAiuiListener getSpecialAiuiListener() {
        return mSpecialAiuiListener;
    }

    public void RRttsComplete(String s) throws RuntimeException{
        if (mRyRRttsListener!=null){
            if(mRyRRttsListener.onComplete(s)){
                mRyRRttsListener = null;
            }
        }
    }

    /**
     * 设置是否需要自动唤醒语音
     * @param autoWakeUp
     */
    public void setAutoWakeUp(boolean autoWakeUp) {
        isAutoWakeUp = autoWakeUp;

    }

    /**
     * 是否自动唤醒语音
     * @return
     */
    public boolean isAutoWakeUp() {
        return isAutoWakeUp;
    }
}
