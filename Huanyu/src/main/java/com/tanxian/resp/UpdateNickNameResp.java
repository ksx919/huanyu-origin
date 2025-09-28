package com.tanxian.resp;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNickNameResp {
    private Long id;
    private String nickname;
    private String token;
}
