package com.tanxian.resp;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAvatarUrlResp {
    private Long id;
    private String avatarUrl;
    private String token;
}
