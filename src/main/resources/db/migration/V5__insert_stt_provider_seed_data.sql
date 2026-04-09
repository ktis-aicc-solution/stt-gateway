INSERT INTO stt_provider_configs
    (provider_name, display_name, is_active, language_code, config_json, supports_streaming, supports_batch, description)
VALUES
    ('google', 'Google Speech-to-Text', TRUE, 'ko-KR',
     '{"credentialsFilePath": "/APP/google-credentials.json", "projectId": ""}',
     TRUE, TRUE, 'Google Cloud Speech-to-Text API. 배치/스트리밍 모두 지원.'),
    ('naver', 'Naver CLOVA Speech', FALSE, 'ko-KR',
     '{"clientId": "", "clientSecret": "", "apiUrl": "https://naveropenapi.apigw.ntruss.com/recog/v1/stt"}',
     FALSE, TRUE, 'Naver CLOVA Speech Recognition API. 배치 방식만 지원.'),
    ('etri', 'ETRI 한국어 STT', FALSE, 'ko-KR',
     '{"apiKey": "", "apiUrl": "http://aiopen.etri.re.kr:8000/WiseASR/Recognition"}',
     FALSE, TRUE, '한국전자통신연구원(ETRI) 한국어 음성인식 API.');
