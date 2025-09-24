class AudioProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.bufferSize = 4096;
        this.buffer = new Float32Array(this.bufferSize);
        this.bufferIndex = 0;
    }

    // Float32Array -> Int16Array (16-bit PCM) 转换函数
    floatTo16BitPCM(input) {
        const output = new Int16Array(input.length);
        for (let i = 0; i < input.length; i++) {
            const s = Math.max(-1, Math.min(1, input[i]));
            output[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        }
        return output;
    }

    process(inputs, outputs, parameters) {
        const input = inputs[0];

        if (input.length > 0) {
            const inputChannel = input[0]; // 获取第一个声道

            // 将数据添加到缓冲区
            for (let i = 0; i < inputChannel.length; i++) {
                this.buffer[this.bufferIndex] = inputChannel[i];
                this.bufferIndex++;

                // 当缓冲区满时，发送数据
                if (this.bufferIndex >= this.bufferSize) {
                    // 转换为16位PCM
                    const int16Pcm = this.floatTo16BitPCM(this.buffer);

                    // 发送到主线程
                    this.port.postMessage({
                        type: 'audioData',
                        data: int16Pcm.buffer
                    });

                    // 重置缓冲区
                    this.bufferIndex = 0;
                }
            }
        }

        return true; // 保持处理器活跃
    }
}

registerProcessor('audio-processor', AudioProcessor);