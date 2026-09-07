use rand::RngCore;
use std::io;
use tokio::io::{AsyncReadExt, AsyncWriteExt};

pub const TLS_RECORD_HEADER_LEN: usize = 5;
pub const MAX_TLS_RECORD_PAYLOAD: usize = 16384;

pub const CONTENT_TYPE_CHANGE_CIPHER_SPEC: u8 = 0x14;
pub const CONTENT_TYPE_ALERT: u8 = 0x15;
pub const CONTENT_TYPE_HANDSHAKE: u8 = 0x16;
pub const CONTENT_TYPE_APPLICATION_DATA: u8 = 0x17;

pub fn is_tls_handshake(buf: &[u8]) -> bool {
    if buf.len() < 3 {
        return false;
    }
    buf[0] == CONTENT_TYPE_HANDSHAKE && buf[1] == 0x03 && (buf[2] >= 0x01 && buf[2] <= 0x04)
}

pub fn build_fake_tls_server_hello(session_id: &[u8; 32]) -> Vec<u8> {
    let mut rng = rand::thread_rng();

    let mut server_random = [0u8; 32];
    rng.fill_bytes(&mut server_random);

    let mut key_share_pub = [0u8; 32];
    rng.fill_bytes(&mut key_share_pub);

    let mut resp = Vec::with_capacity(256);

    resp.extend_from_slice(&[0x16, 0x03, 0x03, 0x00, 0x7a]);
    resp.extend_from_slice(&[0x02, 0x00, 0x00, 0x76]);
    resp.extend_from_slice(&[0x03, 0x03]);
    resp.extend_from_slice(&server_random);

    resp.push(0x20);
    resp.extend_from_slice(session_id);

    resp.extend_from_slice(&[0x13, 0x01]);
    resp.push(0x00);
    resp.extend_from_slice(&[0x00, 0x2e]);
    resp.extend_from_slice(&[0x00, 0x2b, 0x00, 0x02, 0x03, 0x04]);
    resp.extend_from_slice(&[0x00, 0x33, 0x00, 0x24, 0x00, 0x1d, 0x00, 0x20]);
    resp.extend_from_slice(&key_share_pub);

    resp.extend_from_slice(&[0x14, 0x03, 0x03, 0x00, 0x01, 0x01]);

    let mut dummy_app_data = [0u8; 53];
    rng.fill_bytes(&mut dummy_app_data);
    resp.extend_from_slice(&[0x17, 0x03, 0x03, 0x00, 0x35]);
    resp.extend_from_slice(&dummy_app_data);

    resp
}

pub async fn handle_fake_tls_handshake<S: AsyncReadExt + AsyncWriteExt + Unpin>(
    stream: &mut S,
    initial_5: &[u8],
) -> io::Result<()> {
    if initial_5.len() < TLS_RECORD_HEADER_LEN {
        return Err(io::Error::new(
            io::ErrorKind::UnexpectedEof,
            "Invalid TLS header prefix",
        ));
    }

    let record_len = u16::from_be_bytes([initial_5[3], initial_5[4]]) as usize;
    let mut client_hello_body = vec![0u8; record_len];
    stream.read_exact(&mut client_hello_body).await?;

    let mut session_id = [0u8; 32];
    if client_hello_body.len() >= 71 && client_hello_body[38] == 32 {
        session_id.copy_from_slice(&client_hello_body[39..71]);
    } else {
        rand::thread_rng().fill_bytes(&mut session_id);
    }

    let response = build_fake_tls_server_hello(&session_id);
    stream.write_all(&response).await?;
    stream.flush().await?;

    Ok(())
}

pub async fn read_tls_app_data<R: AsyncReadExt + Unpin>(reader: &mut R) -> io::Result<Vec<u8>> {
    loop {
        let mut hdr = [0u8; TLS_RECORD_HEADER_LEN];
        match reader.read_exact(&mut hdr).await {
            Ok(_) => {}
            Err(e) if e.kind() == io::ErrorKind::UnexpectedEof => return Ok(Vec::new()),
            Err(e) => return Err(e),
        }

        let content_type = hdr[0];
        let record_len = u16::from_be_bytes([hdr[3], hdr[4]]) as usize;

        if record_len > MAX_TLS_RECORD_PAYLOAD + 2048 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!("TLS record too large: {}", record_len),
            ));
        }

        let mut payload = vec![0u8; record_len];
        reader.read_exact(&mut payload).await?;

        if content_type == CONTENT_TYPE_APPLICATION_DATA {
            return Ok(payload);
        } else if content_type == CONTENT_TYPE_CHANGE_CIPHER_SPEC
            || content_type == CONTENT_TYPE_HANDSHAKE
        {
            continue;
        } else if content_type == CONTENT_TYPE_ALERT {
            return Ok(Vec::new());
        }
    }
}

pub async fn write_tls_app_data<W: AsyncWriteExt + Unpin>(
    writer: &mut W,
    data: &[u8],
) -> io::Result<()> {
    for chunk in data.chunks(MAX_TLS_RECORD_PAYLOAD) {
        let len = chunk.len() as u16;
        let hdr = [
            CONTENT_TYPE_APPLICATION_DATA,
            0x03,
            0x03,
            (len >> 8) as u8,
            (len & 0xff) as u8,
        ];
        writer.write_all(&hdr).await?;
        writer.write_all(chunk).await?;
    }
    writer.flush().await
}
