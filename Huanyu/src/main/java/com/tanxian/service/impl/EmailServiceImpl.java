package com.tanxian.service.impl;

import com.tanxian.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务实现类
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Value("${spring.mail.username:}")
    private String fromEmail;
    
    @Override
    public boolean sendEmailCode(String email, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("【幻语】邮箱验证码");
            message.setText(buildEmailContent(code));
            
            mailSender.send(message);
            log.info("邮箱验证码发送成功，邮箱: {}", email);
            return true;
        } catch (Exception e) {
            log.error("邮箱验证码发送失败，邮箱: {}", email, e);
            return false;
        }
    }
    
    /**
     * 构建邮件内容
     */
    private String buildEmailContent(String code) {
        return String.format(
            "您好！\n\n" +
            "您的邮箱验证码是：%s\n\n" +
            "验证码有效期为5分钟，请及时使用。\n" +
            "如果这不是您的操作，请忽略此邮件。\n\n" +
            "此邮件由系统自动发送，请勿回复。\n\n" +
            "幻语团队",
            code
        );
    }
}