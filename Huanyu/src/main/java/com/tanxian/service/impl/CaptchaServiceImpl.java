package com.tanxian.service.impl;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.tanxian.exception.BusinessException;
import com.tanxian.exception.BusinessExceptionEnum;
import com.tanxian.resp.CaptchaResp;
import com.tanxian.service.CaptchaService;
import com.tanxian.service.EmailService;
import com.tanxian.util.CaptchaUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;

/**
 * 验证码服务实现类
 */
@Service
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {
    
    @Autowired
    private DefaultKaptcha defaultKaptcha;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private EmailService emailService;
    
    /**
     * 图形验证码过期时间（5分钟）
     */
    private static final Duration CAPTCHA_EXPIRE_TIME = Duration.ofMinutes(5);
    
    /**
     * 邮箱验证码过期时间（5分钟）
     */
    private static final Duration EMAIL_CODE_EXPIRE_TIME = Duration.ofMinutes(5);
    
    @Override
    public CaptchaResp generateCaptcha() {
        try {
            // 生成验证码文本
            String captchaText = defaultKaptcha.createText();
            // 生成验证码图片
            BufferedImage captchaImage = defaultKaptcha.createImage(captchaText);
            
            // 生成验证码ID
            String captchaId = CaptchaUtil.generateCaptchaId();
            
            // 将验证码存储到Redis
            String redisKey = CaptchaUtil.getCaptchaRedisKey(captchaId);
            redisTemplate.opsForValue().set(redisKey, captchaText.toLowerCase(), CAPTCHA_EXPIRE_TIME);
            
            // 将图片转换为Base64
            String imageBase64 = convertImageToBase64(captchaImage);
            
            // 计算过期时间
            long expireTime = System.currentTimeMillis() + CAPTCHA_EXPIRE_TIME.toMillis();
            
            log.info("生成图形验证码成功，ID: {}", captchaId);
            return new CaptchaResp(captchaId, "data:image/png;base64," + imageBase64, expireTime);
        } catch (Exception e) {
            log.error("生成图形验证码失败", e);
            throw new BusinessException(BusinessExceptionEnum.CAPTCHA_GENERATE_FAILED);
        }
    }
    
    @Override
    public boolean verifyCaptcha(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(BusinessExceptionEnum.CAPTCHA_VERIFY_FAILED);
        }
        
        try {
            String redisKey = CaptchaUtil.getCaptchaRedisKey(captchaId);
            String storedCode = redisTemplate.opsForValue().get(redisKey);
            
            if (storedCode == null) {
                throw new BusinessException(BusinessExceptionEnum.CAPTCHA_NOT_FOUND);
            }
            
            if (!storedCode.equals(captchaCode.toLowerCase())) {
                throw new BusinessException(BusinessExceptionEnum.CAPTCHA_VERIFY_FAILED);
            }
            
            // 验证成功后删除验证码
            redisTemplate.delete(redisKey);
            log.info("图形验证码验证成功，ID: {}", captchaId);
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("验证图形验证码时发生异常，ID: {}", captchaId, e);
            throw new BusinessException(BusinessExceptionEnum.CAPTCHA_VERIFY_FAILED);
        }
    }
    
    @Override
    public boolean sendEmailCode(String email, String captchaId, String captchaCode) {
        // 先验证图形验证码
        verifyCaptcha(captchaId, captchaCode);
        
        try {
            // 生成邮箱验证码
            String emailCode = CaptchaUtil.generateEmailCode();
            
            // 发送邮件
            boolean sendSuccess = emailService.sendEmailCode(email, emailCode);
            if (!sendSuccess) {
                throw new BusinessException(BusinessExceptionEnum.EMAIL_SEND_FAILED);
            }
            
            // 将邮箱验证码存储到Redis
            String redisKey = CaptchaUtil.getEmailCodeRedisKey(email);
            redisTemplate.opsForValue().set(redisKey, emailCode, EMAIL_CODE_EXPIRE_TIME);
            
            log.info("邮箱验证码发送成功，邮箱: {}", email);
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送邮箱验证码失败，邮箱: {}", email, e);
            throw new BusinessException(BusinessExceptionEnum.EMAIL_CODE_SEND_FAILED);
        }
    }

    /**
     * 将图片转换为Base64编码
     */
    private String convertImageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
}