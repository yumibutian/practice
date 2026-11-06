package jh.com.doto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 把当前进度交给服务端存档，同时让服务端知道「用户离开了」。
 */
public record InterruptReq(JsonNode progress) {}
