package com.tanxian.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.tanxian.config.AliyunNlsProperties;
import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 批量语音转文字处理器
 *
 * 该类负责处理音频文件的批量语音识别任务，将完整的音频文件转换为文字。
 * 主要用于处理用户上传的音频文件，或用于测试目的。
 * 与WebSocketSpeechTranscriber不同，该类处理的是完整的音频文件而不是实时音频流。
 */
@Service
public class BatchSpeechTranscriber {
    private static volatile String result;
    private String appKey,id,secret,url;
    // 可选：阿里云定制模型与热词词表ID（通过环境变量注入）
    private String customizationId;
    private String vocabularyId;
    private NlsClient client;
    private static final Logger logger = LoggerFactory.getLogger(BatchSpeechTranscriber.class);

    @Autowired
    public BatchSpeechTranscriber(AliyunNlsProperties props) {
        this.appKey = props.getAppKey();
        this.id = props.getAccessKeyId();
        this.secret = props.getAccessKeySecret();
        this.url = (props.getGatewayUrl() == null || props.getGatewayUrl().isBlank())
                ? "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"
                : props.getGatewayUrl();
        this.customizationId = props.getCustomizationId() == null ? "" : props.getCustomizationId().trim();
        this.vocabularyId = props.getVocabularyId() == null ? "" : props.getVocabularyId().trim();

        AccessToken accessToken = new AccessToken(this.id, this.secret);
        try {
            accessToken.apply();
            logger.info("Aliyun NLS token expire time: {}", accessToken.getExpireTime());
            if (this.url.isBlank()) {
                client = new NlsClient(accessToken.getToken());
            } else {
                client = new NlsClient(this.url, accessToken.getToken());
            }
        } catch (IOException e) {
            logger.error("Failed to init NlsClient", e);
        }
    }

    private static SpeechTranscriberListener getTranscriberListener() {
        SpeechTranscriberListener listener = new SpeechTranscriberListener() {
            //识别出中间结果。仅当setEnableIntermediateResult为true时，才会返回该消息。
            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() +
                        ", name: " + response.getName() +
                        //状态码“20000000”表示正常识别。
                        ", status: " + response.getStatus() +
                        //句子编号，从1开始递增。
                        ", index: " + response.getTransSentenceIndex() +
                        //当前的识别结果。
                        ", result: " + response.getTransSentenceText() +
                        //当前已处理的音频时长，单位为毫秒。
                        ", time: " + response.getTransSentenceTime());
            }

            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                //task_id是调用方和服务端通信的唯一标识，遇到问题时，需要提供此task_id。
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());

            }

            //识别出一句话。服务端会智能断句，当识别到一句话结束时会返回此消息。
            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() +
                        ", name: " + response.getName() +
                        //状态码“20000000”表示正常识别。
                        ", status: " + response.getStatus() +
                        //句子编号，从1开始递增。
                        ", index: " + response.getTransSentenceIndex() +
                        //当前的识别结果。
                        ", result: " + response.getTransSentenceText() +
                        //置信度
                        ", confidence: " + response.getConfidence() +
                        //开始时间
                        ", begin_time: " + response.getSentenceBeginTime() +
                        //当前已处理的音频时长，单位为毫秒。
                        ", time: " + response.getTransSentenceTime());
                result+=response.getTransSentenceText();
            }

            //识别完毕
            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                //task_id是调用方和服务端通信的唯一标识，遇到问题时，需要提供此task_id。
                System.out.println("task_id: " + response.getTaskId() +  ", status: " + response.getStatus() + ", status_text: " + response.getStatusText());
            }
        };

        return listener;
    }

    /**
     * 根据二进制数据大小计算对应的同等语音长度
     *
     * @param dataSize 数据大小（字节）
     * @param sampleRate 采样率，支持8000或16000
     * @return 对应的睡眠时间（毫秒）
     */
    public static int getSleepDelta(int dataSize, int sampleRate) {
        // 仅支持16位采样。
        int sampleBytes = 16;
        // 仅支持单通道。
        int soundChannel = 1;
        return (dataSize * 10 * 8000) / (160 * sampleRate);
    }

    /**
     * 处理音频文件并返回识别结果
     *
     * 该方法读取指定路径的音频文件，将其发送到阿里云ASR服务进行语音识别，
     * 并返回完整的识别文本结果。
     *
     * @param filepath 音频文件路径
     * @return 识别后的文本结果
     */
    public String process(String filepath) {
        SpeechTranscriber transcriber = null;
        try {
            // 每次处理前重置累计结果，避免出现 "null" 前缀
            result = "";
            //创建实例、建立连接。
            transcriber = new SpeechTranscriber(client, getTranscriberListener());
            transcriber.setAppKey(appKey);
            //输入音频编码方式。
            transcriber.setFormat(InputFormatEnum.PCM);
            //输入音频采样率。
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            //是否返回中间识别结果。
            transcriber.setEnableIntermediateResult(false);
            //是否生成并返回标点符号。
            transcriber.setEnablePunctuation(true);
            //是否将返回结果规整化，比如将一百返回为100。
            transcriber.setEnableITN(false);

            //设置vad断句参数。默认值：800ms，有效值：200ms～6000ms。
            //transcriber.addCustomedParam("max_sentence_silence", 600);
            //设置是否语义断句。
            //transcriber.addCustomedParam("enable_semantic_sentence_detection",false);
            //设置是否开启过滤语气词，即声音顺滑。
            //transcriber.addCustomedParam("disfluency",true);
            //设置是否开启词模式。
            //transcriber.addCustomedParam("enable_words",true);
            //设置vad噪音阈值参数，参数取值为-1～+1，如-0.9、-0.8、0.2、0.9。
            //取值越趋于-1，判定为语音的概率越大，亦即有可能更多噪声被当成语音被误识别。
            //取值越趋于+1，判定为噪音的越多，亦即有可能更多语音段被当成噪音被拒绝识别。
            //该参数属高级参数，调整需慎重和重点测试。
            //transcriber.addCustomedParam("speech_noise_threshold",0.3);
            //设置训练后的定制语言模型id。
            //transcriber.addCustomedParam("customization_id","你的定制语言模型id");
            //设置训练后的定制热词id。
            //transcriber.addCustomedParam("vocabulary_id","你的定制热词id");

            // 应用定制模型与热词词表（如已配置）
            applyHotwordAndCustomization(transcriber);
            //此方法将以上参数设置序列化为JSON发送给服务端，并等待服务端确认。
            transcriber.start();

            File file = new File(filepath);
            FileInputStream fis = new FileInputStream(file);
            // 跳过 WAV 头（本项目前端生成的 WAV 为 44 字节头）
            long header = 44;
            while (header > 0) {
                long skipped = fis.skip(header);
                if (skipped <= 0) break;
                header -= skipped;
            }
            byte[] b = new byte[3200];
            int len;
            while ((len = fis.read(b)) > 0) {
                logger.info("send data pack length: " + len);
                transcriber.send(b, len);
                //本案例用读取本地文件的形式模拟实时获取语音流并发送的，因为读取速度较快，这里需要设置sleep。
                //如果实时获取语音则无需设置sleep, 如果是8k采样率语音第二个参数设置为8000。
                int deltaSleep = getSleepDelta(len, 16000);
                Thread.sleep(deltaSleep);
            }

            //通知服务端语音数据发送完毕，等待服务端处理完成。
            long now = System.currentTimeMillis();
            logger.info("ASR wait for complete");
            transcriber.stop();
            logger.info("ASR latency : " + (System.currentTimeMillis() - now) + " ms");
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            if (null != transcriber) {
                transcriber.close();
            }
            return result;
        }
    }

    public void shutdown() {
        client.shutdown();
    }

    // 在ASR启动前应用定制模型与热词词表
    /**
     * 应用定制模型与热词词表
     *
     * 在ASR启动前应用定制语言模型和热词词表，以提高特定领域或术语的识别准确率。
     *
     * @param transcriber 语音识别器实例
     */
    private void applyHotwordAndCustomization(SpeechTranscriber transcriber) {
        try {
            if (customizationId != null && !customizationId.isBlank()) {
                logger.info("Apply customization_id: {}", customizationId);
                transcriber.addCustomedParam("customization_id", customizationId);
            }
            if (vocabularyId != null && !vocabularyId.isBlank()) {
                logger.info("Apply vocabulary_id: {}", vocabularyId);
                transcriber.addCustomedParam("vocabulary_id", vocabularyId);
            }
        } catch (Exception ex) {
            logger.warn("Apply hotword/customization failed: {}", ex.toString());
        }
    }

}
