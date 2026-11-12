//! Host tests of the API the Android app uses (`cargo test -p artefact-mobile`).

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};

use super::*;

/// A fresh folder for one test's files.
fn scratch() -> PathBuf {
    static N: AtomicU32 = AtomicU32::new(0);
    let dir = std::env::temp_dir().join(format!("artefact-mobile-test-{}-{}", std::process::id(), N.fetch_add(1, Ordering::Relaxed)));
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

/// A blank PDF of `pages` US Letter pages, written to a fresh folder.
fn blank_pdf(pages: usize) -> String {
    let bytes = pdfcraft_engine::Session::new().create_blank(612.0, 792.0, pages).unwrap();
    let path = scratch().join("blank.pdf");
    std::fs::write(&path, bytes.as_slice()).unwrap();
    path.to_string_lossy().into_owned()
}

#[test]
fn opens_a_document_and_describes_its_pages() {
    let s = MobileSession::new();
    let info = s.open(blank_pdf(3), None).unwrap();
    assert_eq!(info.name, "blank.pdf");
    assert_eq!(info.pages.len(), 3);
    assert_eq!((info.pages[0].width, info.pages[0].height), (612.0, 792.0));
    assert_eq!(info.pages[2].label, "3");
    assert!(!info.dirty);
    assert!(info.undo.is_none());
    assert_eq!(s.documents(), vec![info.id]);
}

#[test]
fn renders_a_page_and_a_tile() {
    let s = MobileSession::new();
    let doc = s.open(blank_pdf(1), None).unwrap().id;
    let img = s.render_page(doc, 0, 0.5, None).unwrap();
    assert_eq!((img.width, img.height), (306, 396));
    assert_eq!(img.rgba.len(), 306 * 396 * 4);
    // a blank page is opaque white
    assert!(img.rgba.chunks(4).all(|p| p == [255, 255, 255, 255]));
    let tile = s.render_page(doc, 0, 2.0, Some(TileRect { x: 100, y: 200, width: 64, height: 32 })).unwrap();
    assert_eq!((tile.width, tile.height), (64, 32));
    // renderers are reused, and again after an edit changes the document
    s.render_page(doc, 0, 0.25, None).unwrap();
    s.execute("page_rotate".into(), format!(r#"{{"doc": {doc}, "degrees": 90}}"#)).unwrap();
    let turned = s.render_page(doc, 0, 0.5, None).unwrap();
    assert_eq!((turned.width, turned.height), (396, 306));
}

#[test]
fn refuses_bad_render_requests() {
    let s = MobileSession::new();
    let doc = s.open(blank_pdf(2), None).unwrap().id;
    assert!(matches!(s.render_page(doc, 2, 1.0, None), Err(MobileError::Render(_))));
    for scale in [0.0, -1.0, f32::NAN, f32::INFINITY] {
        assert!(matches!(s.render_page(doc, 0, scale, None), Err(MobileError::Render(_))), "{scale}");
    }
    assert!(matches!(s.render_page(doc + 1, 0, 1.0, None), Err(MobileError::NoDocument(_))));
    // an absurd scale is capped, not an allocation of terabytes
    let huge = s.render_page(doc, 0, 1.0e9, None).unwrap();
    assert!(huge.width <= pdfcraft_render::MAX_SIDE as u32 && huge.height <= pdfcraft_render::MAX_SIDE as u32);
}

#[test]
fn runs_tools_and_tracks_undo() {
    let s = MobileSession::new();
    let doc = s.open(blank_pdf(2), None).unwrap().id;
    let before = s.document(doc).unwrap().generation;
    let out = s.execute("page_delete".into(), format!(r#"{{"doc": {doc}, "pages": [2]}}"#)).unwrap();
    let v: Value = serde_json::from_str(&out).unwrap();
    assert_eq!(v["pages"], 1);
    let info = s.document(doc).unwrap();
    assert_eq!(info.pages.len(), 1);
    assert!(info.dirty);
    assert!(info.undo.is_some());
    assert_ne!(info.generation, before);
    s.execute("edit_undo".into(), format!(r#"{{"doc": {doc}}}"#)).unwrap();
    assert_eq!(s.document(doc).unwrap().pages.len(), 2);
}

#[test]
fn reports_tool_errors_instead_of_failing_hard() {
    let s = MobileSession::new();
    assert!(matches!(s.execute("no_such_tool".into(), String::new()), Err(MobileError::Command(_))));
    assert!(matches!(s.execute("doc_info".into(), "{not json".into()), Err(MobileError::Command(_))));
    assert!(matches!(s.execute("doc_info".into(), r#"{"doc": 99}"#.into()), Err(MobileError::Command(_))));
    assert!(matches!(s.execute("doc_info".into(), "[1, 2]".into()), Err(MobileError::Command(_))));
    assert!(!s.tools().is_empty());
}

#[test]
fn open_failures_are_told_apart() {
    let s = MobileSession::new();
    assert!(matches!(s.open("relative.pdf".into(), None), Err(MobileError::Open(_))));
    let junk = scratch().join("junk.pdf");
    std::fs::write(&junk, b"this is not a pdf").unwrap();
    assert!(matches!(s.open(junk.to_string_lossy().into_owned(), None), Err(MobileError::Open(_))));
    let missing = scratch().join("missing.pdf");
    assert!(matches!(s.open(missing.to_string_lossy().into_owned(), None), Err(MobileError::Open(_))));
}

#[test]
fn asks_for_a_password_and_opens_with_the_right_one() {
    let s = MobileSession::new();
    let path = blank_pdf(1);
    let doc = s.open(path.clone(), None).unwrap().id;
    s.execute("doc_protect".into(), format!(r#"{{"doc": {doc}, "open_password": "secret"}}"#)).unwrap();
    let locked = scratch().join("locked.pdf").to_string_lossy().into_owned();
    s.execute("doc_save".into(), json!({ "doc": doc, "path": locked }).to_string()).unwrap();
    s.close(doc, true).unwrap();
    assert!(matches!(s.open(locked.clone(), None), Err(MobileError::NeedsPassword)));
    assert!(matches!(s.open(locked.clone(), Some("wrong".into())), Err(MobileError::WrongPassword)));
    let info = s.open(locked, Some("secret".into())).unwrap();
    assert!(info.encrypted);
    let img = s.render_page(info.id, 0, 0.1, None).unwrap();
    assert!(img.width > 0);
}

#[test]
fn flattens_the_outline() {
    let s = MobileSession::new();
    let doc = s.open(blank_pdf(3), None).unwrap().id;
    assert!(s.outline(doc).unwrap().is_empty());
    s.execute("bookmark_add".into(), format!(r#"{{"doc": {doc}, "title": "Intro", "page": 1}}"#)).unwrap();
    s.execute("bookmark_add".into(), format!(r#"{{"doc": {doc}, "title": "End", "page": 3}}"#)).unwrap();
    let outline = s.outline(doc).unwrap();
    assert_eq!(outline.iter().map(|e| (e.title.as_str(), e.level, e.page)).collect::<Vec<_>>(), vec![("Intro", 0, Some(0)), ("End", 0, Some(2))]);
    assert!(s.document(doc).unwrap().has_outline);
}

#[test]
fn closing_refuses_to_lose_edits() {
    let s = MobileSession::new();
    let doc = s.open(blank_pdf(2), None).unwrap().id;
    s.execute("page_delete".into(), format!(r#"{{"doc": {doc}, "pages": [1]}}"#)).unwrap();
    assert!(s.close(doc, false).is_err());
    s.close(doc, true).unwrap();
    assert!(s.documents().is_empty());
    assert!(matches!(s.document(doc), Err(MobileError::NoDocument(_))));
}

/// A PDF made from plain text, written to a fresh folder.
fn text_pdf(text: &str) -> String {
    let bytes = pdfcraft_engine::Session::new().create_from_text("Notes", text).unwrap();
    let path = scratch().join("notes.pdf");
    std::fs::write(&path, bytes.as_slice()).unwrap();
    path.to_string_lossy().into_owned()
}

#[test]
fn selects_words_and_reads_their_text() {
    let s = MobileSession::new();
    let doc = s.open(text_pdf("Hello brave world\nSecond line here"), None).unwrap().id;
    assert!(s.glyph_count(doc, 0).unwrap() > 20);
    let hits = s.find_in_page(doc, 0, "brave".into(), false, false).unwrap();
    assert_eq!(hits.len(), 1);
    let hit = &hits[0];
    assert_eq!(s.text_of(doc, hit.range).unwrap(), "brave");
    assert_eq!(hit.rects.len(), 4);
    assert!(hit.context.contains("Hello brave world"));
    // a long press in the middle of the word selects all of it
    let (cx, cy) = ((hit.rects[0] + hit.rects[2]) / 2.0, (hit.rects[1] + hit.rects[3]) / 2.0);
    let word = s.word_at(doc, 0, cx, cy, 4.0).unwrap().unwrap();
    assert_eq!(word, hit.range);
    // far from any text there is no word
    assert!(s.word_at(doc, 0, 600.0, 780.0, 4.0).unwrap().is_none());
    // a range across the line break reads both lines
    let all = TextRange { page: 0, start: 0, end: s.glyph_count(doc, 0).unwrap() };
    assert_eq!(s.text_of(doc, all).unwrap(), "Hello brave world\nSecond line here");
    assert_eq!(s.text_rects(doc, all).unwrap().len(), 8);
    // out-of-range glyph indices are clamped, not a crash
    let wild = TextRange { page: 0, start: 5, end: u32::MAX };
    assert!(s.text_of(doc, wild).is_ok());
    assert!(s.glyph_rect(doc, 0, u32::MAX).unwrap().is_empty());
    assert!(s.text_of(doc, TextRange { page: 9, start: 0, end: 1 }).is_err());
}

#[test]
fn search_options_and_fresh_text_after_an_edit() {
    let s = MobileSession::new();
    let doc = s.open(text_pdf("Cat cat CAT category"), None).unwrap().id;
    assert_eq!(s.find_in_page(doc, 0, "cat".into(), false, false).unwrap().len(), 4);
    assert_eq!(s.find_in_page(doc, 0, "cat".into(), true, false).unwrap().len(), 2);
    assert_eq!(s.find_in_page(doc, 0, "cat".into(), false, true).unwrap().len(), 3);
    assert!(s.find_in_page(doc, 0, "  ".into(), false, false).unwrap().is_empty());
    let before = s.find_in_page(doc, 0, "Cat".into(), true, false).unwrap()[0].rects.clone();
    // turning the page changes where the text is: the cache follows the document's version
    s.execute("page_rotate".into(), format!(r#"{{"doc": {doc}, "degrees": 90}}"#)).unwrap();
    let after = s.find_in_page(doc, 0, "Cat".into(), true, false).unwrap()[0].rects.clone();
    assert_ne!(before, after);
}

#[test]
fn lists_comments_links_widgets_and_thumbnails() {
    let s = MobileSession::new();
    let path = blank_pdf(2);
    let doc = s.open(path.clone(), None).unwrap().id;
    assert!(s.comments(doc).unwrap().is_empty());
    s.execute(
        "comment_add".into(),
        json!({"doc": doc, "page": 2, "type": "note", "at": [100, 120], "contents": "Look here", "author": "Ann"}).to_string(),
    )
    .unwrap();
    let comments = s.comments(doc).unwrap();
    assert_eq!(comments.len(), 1);
    let c = &comments[0];
    assert_eq!((c.page, c.subtype.as_str(), c.contents.as_deref(), c.author.as_deref()), (1, "Text", Some("Look here"), Some("Ann")));
    // its rectangle is in page points from the top-left, where it was put
    assert!((c.rect[0] - 100.0).abs() < 1.0 && (c.rect[1] - 120.0).abs() < 1.0, "{:?}", c.rect);
    // the index is the one the comment tools take (1-based there)
    s.execute("comment_delete".into(), json!({"doc": doc, "page": c.page + 1, "index": c.index + 1}).to_string()).unwrap();
    assert!(s.comments(doc).unwrap().is_empty());

    s.execute("link_add".into(), json!({"doc": doc, "page": 1, "rect": [50, 60, 150, 80], "to_page": 2}).to_string()).unwrap();
    s.execute("link_add".into(), json!({"doc": doc, "page": 1, "rect": [50, 100, 150, 120], "url": "https://example.org/a"}).to_string()).unwrap();
    let links = s.links(doc, 0).unwrap();
    assert_eq!(links.len(), 2);
    let to_page = links.iter().find(|l| l.page.is_some()).unwrap();
    assert_eq!(to_page.page, Some(1));
    assert!((to_page.rect[1] - 60.0).abs() < 1.0 && (to_page.rect[3] - 80.0).abs() < 1.0, "{:?}", to_page.rect);
    assert_eq!(links.iter().find_map(|l| l.uri.clone()).as_deref(), Some("https://example.org/a"));
    assert!(s.links(doc, 7).unwrap().is_empty());

    s.execute("form_add_field".into(), json!({"doc": doc, "page": 1, "type": "text", "rect": [72, 200, 272, 224], "name": "who"}).to_string())
        .unwrap();
    s.execute("form_add_field".into(), json!({"doc": doc, "page": 1, "type": "checkbox", "rect": [72, 240, 90, 258], "name": "ok"}).to_string())
        .unwrap();
    let widgets = s.widgets(doc).unwrap();
    let who = widgets.iter().find(|w| w.field == "who").unwrap();
    assert_eq!((who.kind.as_str(), who.page), ("text", 0));
    assert!((who.rect[0] - 72.0).abs() < 1.0 && (who.rect[1] - 200.0).abs() < 1.0, "{:?}", who.rect);
    s.execute("form_fill".into(), json!({"doc": doc, "values": {"who": "Bo", "ok": true}}).to_string()).unwrap();
    let widgets = s.widgets(doc).unwrap();
    assert_eq!(widgets.iter().find(|w| w.field == "who").unwrap().value.as_deref(), Some("Bo"));
    assert!(widgets.iter().find(|w| w.field == "ok").unwrap().on);

    let thumb = file_thumbnail(path, 100).unwrap();
    assert!(thumb.height <= 100 && thumb.height >= 99 && thumb.width < thumb.height);
    assert!(file_thumbnail("/no/such/file.pdf".into(), 100).is_err());
    assert!(s.attachments(doc).unwrap().is_empty());
    assert!(s.save_attachment(doc, 0, scratch().join("x").to_string_lossy().into_owned()).is_err());
}

/// A WAV file of 16-bit mono PCM at 16 kHz holding `samples`.
fn wav_file(samples: &[i16]) -> String {
    let data: Vec<u8> = samples.iter().flat_map(|s| s.to_le_bytes()).collect();
    let mut w = Vec::new();
    w.extend_from_slice(b"RIFF");
    w.extend_from_slice(&(36 + data.len() as u32).to_le_bytes());
    w.extend_from_slice(b"WAVEfmt ");
    w.extend_from_slice(&16u32.to_le_bytes());
    w.extend_from_slice(&[1, 0, 1, 0]);
    w.extend_from_slice(&16000u32.to_le_bytes());
    w.extend_from_slice(&32000u32.to_le_bytes());
    w.extend_from_slice(&[2, 0, 16, 0]);
    w.extend_from_slice(b"data");
    w.extend_from_slice(&(data.len() as u32).to_le_bytes());
    w.extend_from_slice(&data);
    let path = scratch().join("voice.wav");
    std::fs::write(&path, w).unwrap();
    path.to_string_lossy().into_owned()
}

#[test]
fn a_voice_comment_keeps_its_recording() {
    let s = MobileSession::new();
    let doc = s.open(blank_pdf(1), None).unwrap().id;
    let samples: Vec<i16> = (0..1600).map(|i| ((i as f32 * 0.2).sin() * 12000.0) as i16).collect();
    let wav = wav_file(&samples);
    let args = serde_json::json!({ "doc": doc, "page": 1, "type": "sound", "at": [100, 100], "path": wav, "author": "Tester" });
    s.execute("comment_add".into(), args.to_string()).unwrap();
    let c = s.comments(doc).unwrap();
    assert_eq!(c.len(), 1);
    assert_eq!(c[0].subtype, "Sound");
    // the icon sits where the page was tapped
    assert!((c[0].rect[0] - 100.0).abs() < 0.5 && (c[0].rect[1] - 100.0).abs() < 0.5, "{:?}", c[0].rect);
    // the recording comes back out as the same samples
    let out = scratch().join("out.wav");
    s.save_sound(doc, 0, c[0].index, out.to_string_lossy().into_owned()).unwrap();
    let back = std::fs::read(&out).unwrap();
    assert_eq!(&back[..4], b"RIFF");
    assert_eq!(u32::from_le_bytes([back[24], back[25], back[26], back[27]]), 16000);
    let pcm: Vec<i16> = back[44..].as_chunks::<2>().0.iter().map(|b| i16::from_le_bytes(*b)).collect();
    assert_eq!(pcm, samples);
    // it survives saving and reopening
    let saved = scratch().join("voice.pdf");
    s.execute("doc_save".into(), serde_json::json!({ "doc": doc, "path": saved, "full": true }).to_string()).unwrap();
    let again = s.open(saved.to_string_lossy().into_owned(), None).unwrap().id;
    let c = s.comments(again).unwrap();
    assert_eq!(c[0].subtype, "Sound");
    s.save_sound(again, 0, c[0].index, out.to_string_lossy().into_owned()).unwrap();
    assert_eq!(std::fs::read(&out).unwrap(), back);
    // not a recording: a clear error, no comment
    let bad = scratch().join("bad.wav");
    std::fs::write(&bad, b"RIFF....WAVEjunk").unwrap();
    let args = serde_json::json!({ "doc": doc, "page": 1, "type": "sound", "at": [10, 10], "path": bad });
    assert!(s.execute("comment_add".into(), args.to_string()).is_err());
    assert!(s.save_sound(doc, 0, 99, out.to_string_lossy().into_owned()).is_err());
}

/// A one-page PDF with a file attached to the document (`data.csv`) and one on a comment (`note.txt`).
fn attachments_pdf() -> String {
    let objects = [
        "<< /Type /Catalog /Pages 2 0 R /Names << /EmbeddedFiles 6 0 R >> >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Annots [4 0 R] >>",
        "<< /Type /Annot /Subtype /FileAttachment /Rect [10 10 30 30] /FS 5 0 R /Contents (note) >>",
        "<< /Type /Filespec /F (note.txt) /EF << /F 7 0 R >> >>",
        "<< /Names [(data.csv) 8 0 R] >>",
        "<< /Type /EmbeddedFile /Length 5 >>\nstream\nhello\nendstream",
        "<< /Type /Filespec /F (data.csv) /EF << /F 9 0 R >> >>",
        "<< /Type /EmbeddedFile /Length 3 >>\nstream\na,b\nendstream",
    ];
    let mut pdf = String::from("%PDF-1.7\n");
    let mut offsets = Vec::new();
    for (i, body) in objects.iter().enumerate() {
        offsets.push(pdf.len());
        pdf.push_str(&format!("{} 0 obj\n{body}\nendobj\n", i + 1));
    }
    let xref = pdf.len();
    pdf.push_str(&format!("xref\n0 {}\n0000000000 65535 f \n", objects.len() + 1));
    for o in offsets {
        pdf.push_str(&format!("{o:010} 00000 n \n"));
    }
    pdf.push_str(&format!("trailer\n<< /Size {} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n", objects.len() + 1));
    let path = scratch().join("attachments.pdf");
    std::fs::write(&path, pdf).unwrap();
    path.to_string_lossy().into_owned()
}

#[test]
fn tells_document_attachments_from_comment_attachments() {
    let s = MobileSession::new();
    let info = s.open(attachments_pdf(), None).unwrap();
    // only the document's own file counts for the Attachments list
    assert_eq!(info.attachments, 1);
    let list = s.attachments(info.id).unwrap();
    let doc_file = list.iter().find(|a| a.name == "data.csv").unwrap();
    let comment_file = list.iter().find(|a| a.name == "note.txt").unwrap();
    assert!(!doc_file.on_page);
    assert!(comment_file.on_page);
}

#[test]
fn reading_blocks_split_paragraphs_and_map_characters_to_glyphs() {
    let s = MobileSession::new();
    let doc = s.open(text_pdf("First paragraph here.\n\nSecond one follows."), None).unwrap().id;
    let blocks = s.reading_blocks(doc, 0).unwrap();
    let texts: Vec<&str> = blocks.iter().map(|b| b.text.as_str()).collect();
    assert_eq!(texts, vec!["First paragraph here.", "Second one follows."]);
    for b in &blocks {
        // one glyph per character, within the block's range and in order
        assert_eq!(b.glyph_of_char.len(), b.text.chars().count());
        assert!(b.glyph_of_char.windows(2).all(|w| w[0] <= w[1]));
        assert!(b.glyph_of_char.iter().all(|&g| g >= b.range.start && g < b.range.end));
    }
    // the word "Second" maps to glyphs whose text is that word
    let second = &blocks[1];
    let range = TextRange { page: 0, start: second.glyph_of_char[0], end: second.glyph_of_char[5] + 1 };
    assert_eq!(s.text_of(doc, range).unwrap(), "Second");
}

#[test]
fn signs_with_a_digital_id_and_trusts_its_signer() {
    let s = MobileSession::new();
    let dir = scratch();
    let id = dir.join("me.p12").to_string_lossy().into_owned();
    let made = json!({ "name": "Test Signer", "email": "signer@example.com", "password": "secret12", "path": id });
    s.execute("sign_id_create".into(), made.to_string()).unwrap();
    // the file's certificate, and a wrong password told apart
    let info = digital_id_info(id.clone(), "secret12".into()).unwrap();
    assert_eq!(info.name, "Test Signer");
    assert_eq!(info.email.as_deref(), Some("signer@example.com"));
    assert!(info.self_signed && info.valid_to > info.valid_from && info.problem.is_none());
    assert_eq!(info.sha256.replace(' ', "").len(), 64);
    assert!(matches!(digital_id_info(id.clone(), "wrong".into()), Err(MobileError::WrongPassword)));
    let junk = dir.join("junk.p12");
    std::fs::write(&junk, b"not a digital id").unwrap();
    assert!(matches!(digital_id_info(junk.to_string_lossy().into_owned(), "x".into()), Err(MobileError::Command(_))));

    // signing writes the signed file over the working copy the document was opened from
    let path = blank_pdf(1);
    let doc = s.open(path.clone(), None).unwrap().id;
    assert!(s.signer_certificate(doc, "Signature1".into()).unwrap().is_none());
    let args =
        json!({ "doc": doc, "id": id, "password": "secret12", "page": 1, "rect": [72, 72, 252, 122], "graphic": { "text": "Test" }, "out": path });
    let out: Value = serde_json::from_str(&s.execute("sign_document".into(), args.to_string()).unwrap()).unwrap();
    let field = out["signature"]["field"].as_str().unwrap().to_string();
    assert_eq!(out["signature"]["status"], "unknown");
    let info = s.document(doc).unwrap();
    assert!(info.signed && !info.dirty && info.undo.is_none());
    assert_eq!(std::fs::read(&path).unwrap().len() as u64, out["bytes"].as_u64().unwrap());

    // trusting the signer's certificate makes the signature valid
    let cert = s.signer_certificate(doc, field).unwrap().unwrap();
    let cer = dir.join("signer.cer");
    std::fs::write(&cer, &cert).unwrap();
    s.execute("sign_trust".into(), json!({ "paths": [cer.to_string_lossy()] }).to_string()).unwrap();
    let list: Value = serde_json::from_str(&s.execute("sign_list".into(), json!({ "doc": doc }).to_string()).unwrap()).unwrap();
    assert_eq!(list["all_valid"], true);

    // an edit after signing is saved incrementally: the signature stays valid
    s.execute("comment_add".into(), json!({ "doc": doc, "page": 1, "type": "note", "at": [300, 300], "contents": "after" }).to_string()).unwrap();
    s.execute("doc_save".into(), json!({ "doc": doc }).to_string()).unwrap();
    let list: Value = serde_json::from_str(&s.execute("sign_list".into(), json!({ "doc": doc }).to_string()).unwrap()).unwrap();
    assert_eq!(list["signatures"][0]["status"], "valid");
    assert_eq!(list["signatures"][0]["modification"], "allowed");
}

/// A form's scripts run through the calls the app makes: filling a field recalculates a scripted
/// total and returns its alert, and a button's script (from `form_actions`) runs with `js_run`.
#[test]
fn form_scripts_run_when_fields_are_filled_and_buttons_pressed() {
    let s = MobileSession::new();
    let doc = s.open(blank_pdf(1), None).unwrap().id;
    let run = |tool: &str, args: Value| -> Value { serde_json::from_str(&s.execute(tool.into(), args.to_string()).unwrap()).unwrap() };
    for (i, name) in ["a", "b", "total"].into_iter().enumerate() {
        let y = 100 + 30 * i;
        run("form_add_field", json!({"doc": doc, "page": 1, "type": "text", "rect": [200, y, 360, y + 20], "name": name}));
    }
    run("form_add_field", json!({"doc": doc, "page": 1, "type": "button", "rect": [200, 200, 360, 224], "name": "double", "caption": "Double a"}));
    let sum = "event.value = Number(this.getField(\"a\").value) + Number(this.getField(\"b\").value); if (event.value > 9) app.alert(\"Big\");";
    run("form_set_script", json!({"doc": doc, "field": "total", "event": "calculate", "script": sum}));
    let double = "var a = this.getField(\"a\"); a.value = Number(a.value) * 2; app.alert(\"a is now \" + a.value);";
    run("form_set_script", json!({"doc": doc, "field": "double", "event": "mouse_up", "script": double}));
    let total = |s: &MobileSession| s.widgets(doc).unwrap().into_iter().find(|w| w.field == "total").and_then(|w| w.value);

    run("form_fill", json!({"doc": doc, "values": {"a": "2"}}));
    let filled = run("form_fill", json!({"doc": doc, "values": {"b": "3"}}));
    assert_eq!(total(&s).as_deref(), Some("5"), "{filled}");
    let actions = run("form_actions", json!({"doc": doc, "field": "double"}));
    let script = actions["actions"][0]["javascript"].as_str().unwrap().to_owned();
    let pressed = run("js_run", json!({"doc": doc, "field": "double", "script": script}));
    assert_eq!(pressed["alerts"], json!(["a is now 4"]), "{pressed}");
    assert_eq!(total(&s).as_deref(), Some("7"));
    let big = run("form_fill", json!({"doc": doc, "values": {"b": "8"}}));
    assert_eq!(big["alerts"], json!(["Big"]), "{big}");
}

/// A Word document (.docx) holding `text`, written by WordCraft into a fresh folder as `name`.
fn word_file(text: &str, name: &str) -> String {
    let doc = wordcraft_engine::doc::Document::from_text(text);
    let bytes = wordcraft_engine::io::save_bytes("doc.docx", &doc).unwrap();
    let path = scratch().join(name);
    std::fs::write(&path, bytes).unwrap();
    path.to_string_lossy().into_owned()
}

#[test]
fn opens_a_word_document_beside_the_pdfs() {
    let s = MobileSession::new();
    let pdf = s.open(blank_pdf(1), None).unwrap();
    let word = s.open(word_file("Quarterly results were strong.", "Report.docx"), None).unwrap();
    assert_eq!(word.kind, DocumentKind::Word);
    assert_eq!(pdf.kind, DocumentKind::Pdf);
    assert_ne!(word.id, pdf.id);
    assert_eq!(word.name, "Report.docx");
    assert_eq!(word.pages.len(), 1);
    assert!(word.pages[0].width > 500.0 && word.pages[0].height > 700.0, "{:?}", word.pages[0]);
    assert!(word.read_only_reason.is_none(), "Word documents are editable");
    assert_eq!(s.documents(), vec![pdf.id, word.id]);
    assert_eq!(s.document(word.id).unwrap().pages.len(), 1);
    // the PDF-only lists are empty, not errors
    assert!(s.comments(word.id).unwrap().is_empty());
    assert!(s.widgets(word.id).unwrap().is_empty());
    assert!(s.outline(word.id).unwrap().is_empty());
    s.close(word.id, false).unwrap();
    assert_eq!(s.documents(), vec![pdf.id]);
}

#[test]
fn a_word_document_is_told_by_its_bytes_not_its_name() {
    // a working copy named as a PDF is still read as the Word document it is
    let s = MobileSession::new();
    let info = s.open(word_file("Hello.", "Letter.pdf"), None).unwrap();
    assert_eq!(info.kind, DocumentKind::Word);
}

#[test]
fn renders_a_word_page_and_a_tile_with_its_text() {
    let s = MobileSession::new();
    let doc = s.open(word_file("Inked words on the page.", "Ink.docx"), None).unwrap();
    let page = s.render_page(doc.id, 0, 1.0, None).unwrap();
    assert_eq!((page.width, page.height), (doc.pages[0].width.ceil() as u32, doc.pages[0].height.ceil() as u32));
    assert_eq!(page.rgba.len() as u32, page.width * page.height * 4);
    // the words are drawn: some pixel of the page is dark
    let dark = page.rgba.as_chunks::<4>().0.iter().any(|p| p[0] < 100 && p[3] == 255);
    assert!(dark, "nothing drawn on the page");
    let tile = s.render_page(doc.id, 0, 2.0, Some(TileRect { x: 100, y: 100, width: 64, height: 32 })).unwrap();
    assert_eq!((tile.width, tile.height), (64, 32));
    assert!(s.render_page(doc.id, 5, 1.0, None).is_err());
}

#[test]
fn a_word_page_can_be_searched_selected_and_read() {
    let s = MobileSession::new();
    let doc = s.open(word_file("The quick brown fox jumps over the lazy dog.", "Fox.docx"), None).unwrap().id;
    assert!(s.glyph_count(doc, 0).unwrap() > 30);
    assert_eq!(s.page_plain_text(doc, 0).unwrap().trim(), "The quick brown fox jumps over the lazy dog.");
    let hits = s.find_in_page(doc, 0, "brown fox".into(), false, false).unwrap();
    assert_eq!(hits.len(), 1);
    let hit = hits.first().unwrap();
    assert_eq!(s.text_of(doc, hit.range).unwrap(), "brown fox");
    assert_eq!(hit.rects.len(), 4);
    // a long press on "brown" selects the word
    let [x0, y0, x1, y1] = [hit.rects[0], hit.rects[1], hit.rects[2], hit.rects[3]];
    let word = s.word_at(doc, 0, x0 + 2.0, (y0 + y1) / 2.0, 2.0).unwrap().unwrap();
    assert_eq!(s.text_of(doc, word).unwrap(), "brown");
    assert!(x1 > x0);
    let blocks = s.reading_blocks(doc, 0).unwrap();
    assert_eq!(blocks.len(), 1);
    assert_eq!(blocks[0].text, "The quick brown fox jumps over the lazy dog.");
}

#[test]
fn a_word_thumbnail_fits_the_size_asked() {
    let img = file_thumbnail(word_file("Thumb.", "Thumb.docx"), 120).unwrap();
    assert_eq!(img.width.max(img.height), 120);
}

#[test]
#[ignore = "renders the file in ARTEFACT_SAMPLE to raw RGBA beside it, for a look"]
fn render_sample() {
    let Ok(path) = std::env::var("ARTEFACT_SAMPLE") else { return };
    let s = MobileSession::new();
    let info = s.open(path.clone(), None).unwrap();
    eprintln!("{:?} pages {} title {:?}", info.kind, info.pages.len(), info.title);
    for p in 0..info.pages.len() as u32 {
        let img = s.render_page(info.id, p, 1.5, None).unwrap();
        std::fs::write(format!("{path}.{p}.{}x{}.rgba", img.width, img.height), &img.rgba).unwrap();
        eprintln!("hits {:?}", s.find_in_page(info.id, p, "revenue".into(), false, false).unwrap().len());
        eprintln!("links {:?}", s.links(info.id, p).unwrap());
    }
}

/// A presentation (.pptx) whose title slide reads `title`, written by DeckCraft into a fresh folder.
fn deck_file(title: &str, name: &str) -> String {
    let mut p = deckcraft_model::defaults::new_presentation(None);
    let slide = std::sync::Arc::make_mut(p.slides.first_mut().unwrap());
    let shape = slide.shapes.iter_mut().find(|s| s.ph.is_some()).unwrap();
    shape.text = Some(deckcraft_model::text::TextBody::from_text(title));
    let path = scratch().join(name);
    std::fs::write(&path, deckcraft_pptx::export(&p).unwrap()).unwrap();
    path.to_string_lossy().into_owned()
}

#[test]
fn opens_a_presentation_as_slides() {
    let s = MobileSession::new();
    let info = s.open(deck_file("Quarterly results", "Deck.pptx"), None).unwrap();
    assert_eq!(info.kind, DocumentKind::Slides);
    assert_eq!(info.pages.len(), 1);
    assert!(info.pages[0].width > info.pages[0].height, "a slide is wide: {:?}", info.pages[0]);
    let page = s.render_page(info.id, 0, 0.5, None).unwrap();
    assert_eq!(page.rgba.len() as u32, page.width * page.height * 4);
    let tile = s.render_page(info.id, 0, 1.0, Some(TileRect { x: 10, y: 10, width: 40, height: 20 })).unwrap();
    assert_eq!((tile.width, tile.height), (40, 20));
    assert!(s.render_page(info.id, 1, 1.0, None).is_err());
    let hits = s.find_in_page(info.id, 0, "results".into(), false, false).unwrap();
    assert_eq!(hits.len(), 1);
    assert_eq!(s.text_of(info.id, hits[0].range).unwrap(), "results");
    assert_eq!(file_thumbnail(deck_file("Thumb", "T.pptx"), 100).unwrap().width, 100);
}

/// A workbook: `csv` read by GridCraft and written as `name` (.csv as it is, .xlsx by its writer).
fn book_file(csv: &str, name: &str) -> String {
    let path = scratch().join(name);
    let bytes = if name.ends_with(".csv") {
        csv.as_bytes().to_vec()
    } else {
        let (wb, _) = gridcraft_engine::io::open_bytes("t.csv", csv.as_bytes()).unwrap();
        gridcraft_engine::io::save_bytes(&wb, name).unwrap()
    };
    std::fs::write(&path, bytes).unwrap();
    path.to_string_lossy().into_owned()
}

#[test]
fn opens_a_workbook_and_serves_its_cells() {
    let s = MobileSession::new();
    for name in ["Prices.xlsx", "Prices.csv"] {
        let info = s.open(book_file("Fruit,Price\nApples,3\nPears,4.5\n", name), None).unwrap();
        assert_eq!(info.kind, DocumentKind::Sheets, "{name}");
        let sheets = s.sheets(info.id).unwrap();
        assert_eq!(sheets.len(), 1);
        assert_eq!((sheets[0].rows, sheets[0].cols), (3, 2));
        let cells = s.sheet_cells(info.id, 0, 0, 10, 0, 5).unwrap();
        let text = |r: u32, c: u32| cells.iter().find(|x| x.row == r && x.col == c).map(|x| x.text.clone());
        assert_eq!(text(1, 0).as_deref(), Some("Apples"));
        assert_eq!(text(2, 1).as_deref(), Some("4.5"));
        // General: numbers right, text left
        assert_eq!(cells.iter().find(|x| x.text == "3").map(|x| x.align.as_str()), Some("r"));
        assert_eq!(cells.iter().find(|x| x.text == "Pears").map(|x| x.align.as_str()), Some("l"));
        let axes = s.sheet_axes(info.id, 0, 0, 3, 0, 2).unwrap();
        assert!(axes.col_widths.iter().all(|w| *w > 20.0) && axes.row_heights.iter().all(|h| *h > 8.0), "{axes:?}");
        let hits = s.sheet_find(info.id, 0, "pear".into(), false, 10).unwrap();
        assert_eq!(hits.len(), 1);
        assert_eq!((hits[0].row0, hits[0].col0), (2, 0));
        assert!(s.sheet_cells(info.id, 0, 0, 1000, 0, 1000).is_err(), "a window too large is refused");
        assert!(s.render_page(info.id, 0, 0.5, None).is_ok());
    }
    let pdf = s.open(blank_pdf(1), None).unwrap();
    assert!(s.sheets(pdf.id).is_err());
}

#[test]
fn develops_a_photo_and_saves_it() {
    let (w, h) = (64usize, 48usize);
    let img = lightcraft_raster::Rgba8 { width: w, height: h, data: vec![[90u8, 110, 130, 255]; w * h] };
    let png = lightcraft_codecs::encode_png(&lightcraft_codecs::EncodeImage::rgba8(&img), &lightcraft_codecs::EncodeMeta::default()).unwrap();
    let dir = scratch();
    let path = dir.join("photo.png").to_string_lossy().into_owned();
    std::fs::write(&path, png).unwrap();
    let e = PhotoEditor::new();
    let plain = e.render(path.clone(), String::new(), 32).unwrap();
    assert_eq!((plain.width, plain.height), (32, 24));
    let bright = e.render(path.clone(), r#"{"light":{"exposure":1.5}}"#.into(), 32).unwrap();
    let mean = |r: &RenderedImage| r.rgba.iter().step_by(4).map(|v| u32::from(*v)).sum::<u32>() / (r.width * r.height);
    assert!(mean(&bright) > mean(&plain) + 10, "{} vs {}", mean(&bright), mean(&plain));
    assert!(e.render(path.clone(), "{not json".into(), 32).is_err());
    let out = dir.join("out.jpg").to_string_lossy().into_owned();
    e.export_jpeg(path, r#"{"light":{"exposure":0.5}}"#.into(), out.clone(), 90).unwrap();
    assert!(std::fs::read(out).unwrap().starts_with(&[0xFF, 0xD8]));
}

#[test]
fn office_documents_export_to_pdf_and_their_own_formats() {
    let s = MobileSession::new();
    let dir = scratch();
    let cases = [
        (word_file("Exported words.", "W.docx"), vec!["pdf", "docx", "odt", "rtf", "html", "md", "txt"]),
        (deck_file("Exported slide", "S.pptx"), vec!["pdf", "pptx"]),
        (book_file("a,b\n1,2\n", "B.xlsx"), vec!["pdf", "xlsx", "csv"]),
    ];
    for (path, formats) in cases {
        let id = s.open(path.clone(), None).unwrap().id;
        assert_eq!(s.export_formats(id), formats, "{path}");
        for f in &formats {
            let out = dir.join(format!("out.{f}")).to_string_lossy().into_owned();
            s.export_office(id, f.to_string(), out.clone()).unwrap_or_else(|e| panic!("{path} as {f}: {e}"));
            let bytes = std::fs::read(&out).unwrap();
            assert!(!bytes.is_empty(), "{path} as {f}");
            if *f == "pdf" {
                assert!(bytes.starts_with(b"%PDF"), "{path} as pdf");
                // and it opens in the PDF engine
                assert!(!s.open(out.clone(), None).unwrap().pages.is_empty());
            }
        }
        assert!(s.export_office(id, "exe".into(), dir.join("x").to_string_lossy().into_owned()).is_err());
    }
}

#[test]
fn a_workbook_is_edited_recalculated_undone_and_saved() {
    let s = MobileSession::new();
    let path = book_file("Item,Price\nApples,3\nPears,4\n", "Edit.xlsx");
    let id = s.open(path.clone(), None).unwrap().id;
    let text = |r: u32, c: u32| s.sheet_cells(id, 0, 0, 10, 0, 5).unwrap().into_iter().find(|x| x.row == r && x.col == c).map(|x| x.text);
    s.sheet_set_cell(id, 0, 3, 1, "=SUM(B2:B3)".into()).unwrap();
    assert_eq!(text(3, 1).as_deref(), Some("7"));
    assert_eq!(s.sheet_input(id, 0, 3, 1).unwrap(), "=SUM(B2:B3)");
    // a changed input recalculates what depends on it
    s.sheet_set_cell(id, 0, 1, 1, "10".into()).unwrap();
    assert_eq!(text(3, 1).as_deref(), Some("14"));
    let b2 = CellSpan { row0: 1, col0: 1, row1: 1, col1: 1 };
    s.sheet_command(id, 0, b2, "home.bold".into(), String::new()).unwrap();
    assert!(s.sheet_cells(id, 0, 1, 1, 1, 1).unwrap()[0].bold);
    let info = s.document(id).unwrap();
    assert!(info.dirty && info.undo.is_some());
    s.sheet_command(id, 0, b2, "edit.undo".into(), String::new()).unwrap();
    assert!(!s.sheet_cells(id, 0, 1, 1, 1, 1).unwrap()[0].bold);
    s.save_office(id, path.clone()).unwrap();
    assert!(!s.document(id).unwrap().dirty);
    let again = s.open(path, None).unwrap().id;
    assert_eq!(s.sheet_input(again, 0, 3, 1).unwrap(), "=SUM(B2:B3)");
    assert!(s.sheet_command(id, 0, b2, "no.such.command".into(), String::new()).is_err());
}

#[test]
fn a_word_document_is_typed_in_formatted_undone_and_saved() {
    let s = MobileSession::new();
    let path = word_file("Hello world", "Typing.docx");
    let id = s.open(path.clone(), None).unwrap().id;
    let before = s.document(id).unwrap().generation;
    // a tap at the end of the line puts the caret there; typing goes in where it is
    let hits = s.find_in_page(id, 0, "world".into(), false, false).unwrap();
    let r = &hits[0].rects;
    s.word_command(id, "caret.set".into(), format!(r#"{{"page":0,"x":{},"y":{}}}"#, r[2] + 1.0, (r[1] + r[3]) / 2.0)).unwrap();
    let caret = s.word_caret(id).unwrap();
    assert_eq!(caret.page, 0);
    assert!(caret.height > 4.0 && caret.selection.is_empty());
    s.word_command(id, "text.insert".into(), r#"{"text":", again"}"#.into()).unwrap();
    assert_eq!(s.page_plain_text(id, 0).unwrap().trim(), "Hello world, again");
    let info = s.document(id).unwrap();
    assert!(info.dirty && info.undo.is_some() && info.generation != before);
    // select all and make it bold: the selection has rectangles
    s.word_command(id, "select.all".into(), String::new()).unwrap();
    assert!(!s.word_caret(id).unwrap().selection.is_empty());
    s.word_command(id, "format.bold".into(), String::new()).unwrap();
    s.word_command(id, "edit.undo".into(), String::new()).unwrap();
    s.word_command(id, "edit.undo".into(), String::new()).unwrap();
    assert_eq!(s.page_plain_text(id, 0).unwrap().trim(), "Hello world");
    s.word_command(id, "edit.redo".into(), String::new()).unwrap();
    s.save_office(id, path.clone()).unwrap();
    assert!(!s.document(id).unwrap().dirty);
    let again = s.open(path, None).unwrap().id;
    assert_eq!(s.page_plain_text(again, 0).unwrap().trim(), "Hello world, again");
    assert!(s.word_command(id, "no.such.command".into(), String::new()).is_err());
}

#[test]
fn a_powerpoint_97_2003_file_opens_with_its_slides_text() {
    let s = MobileSession::new();
    let path = scratch().join("Old.ppt");
    std::fs::write(&path, crate::ppt::tests::sample_ppt()).unwrap();
    let info = s.open(path.to_string_lossy().into_owned(), None).unwrap();
    assert_eq!(info.kind, DocumentKind::Slides);
    assert_eq!(info.pages.len(), 2);
    assert_eq!(s.find_in_page(info.id, 1, "new markets".into(), false, false).unwrap().len(), 1);
    assert!(s.render_page(info.id, 0, 0.5, None).is_ok());
}

#[test]
fn a_word_paragraph_is_edited_in_place() {
    let s = MobileSession::new();
    let id = s.open(word_file("First line here\nSecond line", "Para.docx"), None).unwrap().id;
    let hit = s.find_in_page(id, 0, "First".into(), false, false).unwrap().remove(0);
    let p = s.word_paragraph_at(id, 0, hit.rects[0] + 2.0, (hit.rects[1] + hit.rects[3]) / 2.0).unwrap().unwrap();
    assert_eq!(p.text, "First line here");
    assert!(p.x1 > p.x0 && p.y1 > p.y0);
    s.word_command(id, "text.insert".into(), r#"{"text":"A new first line"}"#.into()).unwrap();
    let text = s.page_plain_text(id, 0).unwrap();
    assert!(text.contains("A new first line") && text.contains("Second line") && !text.contains("First line here"), "{text}");
}

#[test]
#[ignore = "prints what the workbook in ARTEFACT_SAMPLE holds, for a look"]
fn dump_sheet_sample() {
    let Ok(path) = std::env::var("ARTEFACT_SAMPLE") else { return };
    let bytes = std::fs::read(&path).unwrap();
    let (wb, warnings) = gridcraft_engine::io::open_bytes_with_password(&path, &bytes, None).unwrap();
    eprintln!("warnings {warnings:?}");
    for (i, s) in wb.sheets.iter().enumerate() {
        eprintln!(
            "sheet {i} {:?} used {:?} merges {} cf {} tables {} charts {} images {} shapes {} gridlines {} freeze {:?} zoom {} cols {} rows {}",
            s.name,
            s.used_range().map(|r| r.a1()),
            s.merges.len(),
            s.cond_formats.len(),
            s.tables.len(),
            s.charts.len(),
            s.images.len(),
            s.shapes.len(),
            s.show_gridlines,
            s.freeze,
            s.zoom,
            s.cols.len(),
            s.rows.len()
        );
        let Some(used) = s.used_range() else { continue };
        let mut fills = std::collections::BTreeMap::new();
        let mut borders = std::collections::BTreeMap::new();
        let mut fonts = std::collections::BTreeMap::new();
        let mut aligns = std::collections::BTreeMap::new();
        for row in 0..=used.end.row {
            for col in 0..=used.end.col {
                let st = wb.styles.get(s.style_id(gridcraft_engine::core::CellRef::new(row, col)));
                *fills
                    .entry(format!("{:?} {:?} {:?}", st.fill.pattern, st.fill.fg.resolve(&wb.theme), st.fill.bg.resolve(&wb.theme)))
                    .or_insert(0) += 1;
                for b in [&st.borders.left, &st.borders.top, &st.borders.right, &st.borders.bottom, &st.borders.diag_down, &st.borders.diag_up] {
                    *borders.entry(format!("{:?} {:?}", b.style, b.color)).or_insert(0) += 1;
                }
                *fonts
                    .entry(format!("{} {} b{} i{} {:?} {:?}", st.font.name, st.font.size, st.font.bold, st.font.italic, st.font.color, st.font.vert))
                    .or_insert(0) += 1;
                *aligns.entry(format!("{:?}", st.align)).or_insert(0) += 1;
            }
        }
        eprintln!("fills {fills:#?}\nborders {borders:#?}\nfonts {fonts:#?}\naligns {aligns:#?}");
    }
}

#[test]
fn a_deck_font_missing_on_a_phone_gets_a_stand_in_of_its_kind() {
    // a database that sees no installed fonts, as on a phone, given the faces Artefact bundles
    let db = deckcraft_fonts::FontDb::with_font_dirs(Vec::new());
    db.set_system_fallback(false);
    for bytes in wordcraft_fonts::bundled() {
        db.add_font(bytes.to_vec());
    }
    assert_eq!(db.document_face("Calibri", "Regular").family, "Source Sans 3");
    assert_eq!(db.document_face("Times New Roman", "Bold").family, "Source Serif 4");
    assert_eq!(db.document_face("Arial", "Regular").family, "Inter");
    assert_eq!(db.document_face("Consolas", "Regular").family, "JetBrains Mono");
    // a family nobody has heard of is drawn as Office draws it: in its default sans
    assert_eq!(db.document_face("Some Unknown Face", "Regular").family, "Source Sans 3");
    assert_ne!(db.document_face("Georgia", "Italic").family, deckcraft_fonts::LAST_RESORT_FAMILY);
}

#[test]
fn a_password_protected_presentation_asks_for_its_password() {
    let plain = std::fs::read(deck_file("Locked away", "Plain.pptx")).unwrap();
    let locked = gridcraft_xlsx::testing::encrypt_workbook(&plain, "s3cret");
    let path = scratch().join("Locked.pptx");
    std::fs::write(&path, locked).unwrap();
    let path = path.to_string_lossy().into_owned();
    let s = MobileSession::new();
    assert!(matches!(s.open(path.clone(), None), Err(MobileError::NeedsPassword)));
    assert!(matches!(s.open(path.clone(), Some("wrong".into())), Err(MobileError::WrongPassword)));
    let info = s.open(path, Some("s3cret".into())).unwrap();
    assert_eq!(info.kind, DocumentKind::Slides);
    assert!(info.encrypted);
    assert_eq!(s.find_in_page(info.id, 0, "locked".into(), false, false).unwrap().len(), 1);
}

#[test]
fn the_photo_editors_tools_are_settings_lightcraft_reads() {
    // the names the app writes for its Straighten, Whites, Blacks, Clarity, Sharpen and Vignette tools (snake case, as its settings are)
    let json = r#"{"geometry":{"rotate":4.5},"light":{"whites":20,"blacks":-10},"effects":{"clarity":30},"detail":{"sharpen_amount":60},"vignette":{"amount":-40}}"#;
    let s: lightcraft_develop::DevelopSettings = serde_json::from_str(json).unwrap();
    assert_eq!(s.geometry.rotate, 4.5);
    assert_eq!((s.light.whites, s.light.blacks), (20.0, -10.0));
    assert_eq!(s.effects.clarity, 30.0);
    assert_eq!(s.detail.sharpen_amount, 60.0);
    assert_eq!(s.vignette.amount, -40.0);
    // Crop: a box of the picture, as fractions of it
    let crop: lightcraft_develop::DevelopSettings =
        serde_json::from_str(r#"{"crop":{"geometry":{"rect":{"x0":0.125,"y0":0.0,"x1":0.875,"y1":1.0},"angle":0.0}}}"#).unwrap();
    assert_eq!((crop.crop.geometry.rect.x0, crop.crop.geometry.rect.x1), (0.125, 0.875));
}

/// A link in each kind of document the viewer opens, found where it is drawn.
#[test]
fn links_are_found_in_every_kind_of_document() {
    let s = MobileSession::new();
    let uri_of = |links: &[LinkInfo]| links.iter().filter_map(|l| l.uri.clone()).collect::<Vec<_>>();

    // Markdown, read by the Word engine
    let md = scratch().join("Links.md");
    std::fs::write(&md, "# Title\n\nSee [the site](https://example.com/md) and [mail](mailto:a@example.com).\n").unwrap();
    let info = s.open(md.to_string_lossy().into_owned(), None).unwrap();
    let found = uri_of(&s.links(info.id, 0).unwrap());
    assert!(found.contains(&"https://example.com/md".to_string()), "md: {found:?}");
    assert!(found.contains(&"mailto:a@example.com".to_string()), "md: {found:?}");

    // the same as a .docx (written by the Word engine)
    let docx = scratch().join("Links.docx");
    s.export_office(info.id, "docx".into(), docx.to_string_lossy().into_owned()).unwrap();
    let d = s.open(docx.to_string_lossy().into_owned(), None).unwrap();
    assert!(uri_of(&s.links(d.id, 0).unwrap()).contains(&"https://example.com/md".to_string()), "docx");

    // a presentation: a web link on text, and a shape that goes to the next slide
    let link = |action| deckcraft_model::text::Hyperlink {
        action,
        tooltip: String::new(),
        highlight_click: false,
        sound: None,
        stop_sound: false,
        zoom_return: None,
    };
    let mut p = deckcraft_model::defaults::new_presentation(None);
    let layout = p.slides[0].layout;
    let second = deckcraft_model::defaults::new_slide(&mut p, layout).unwrap();
    p.slides.push(std::sync::Arc::new(second));
    {
        let slide = std::sync::Arc::make_mut(p.slides.first_mut().unwrap());
        let shape = slide.shapes.iter_mut().find(|s| s.ph.is_some()).unwrap();
        let mut body = deckcraft_model::text::TextBody::from_text("Open the site");
        body.paragraphs[0].runs[0].props.link = Some(link(deckcraft_model::text::Action::Url { url: "https://example.com/deck".into() }));
        shape.text = Some(body);
        let other = slide.shapes.iter_mut().filter(|s| s.ph.is_some()).nth(1).unwrap();
        other.text = Some(deckcraft_model::text::TextBody::from_text("Next"));
        other.click = Some(link(deckcraft_model::text::Action::NextSlide));
    }
    let pptx = scratch().join("Links.pptx");
    std::fs::write(&pptx, deckcraft_pptx::export(&p).unwrap()).unwrap();
    let deck = s.open(pptx.to_string_lossy().into_owned(), None).unwrap();
    let links = s.links(deck.id, 0).unwrap();
    assert!(uri_of(&links).contains(&"https://example.com/deck".to_string()), "pptx: {links:?}");
    assert!(links.iter().any(|l| l.page == Some(1)), "pptx jump: {links:?}");

    // a workbook: a cell's hyperlink comes with the cell
    let (mut wb, _) = gridcraft_engine::io::open_bytes("t.csv", b"Site,Sheet\nhere,there\n").unwrap();
    std::sync::Arc::make_mut(&mut wb.sheets[0]).hyperlinks.insert(
        gridcraft_engine::core::CellRef::new(1, 0),
        gridcraft_engine::model::Hyperlink { target: "https://example.com/sheet".into(), tooltip: None, external: true },
    );
    let xlsx = scratch().join("Links.xlsx");
    std::fs::write(&xlsx, gridcraft_engine::io::save_bytes(&wb, "Links.xlsx").unwrap()).unwrap();
    let book = s.open(xlsx.to_string_lossy().into_owned(), None).unwrap();
    let cells = s.sheet_cells(book.id, 0, 0, 5, 0, 5).unwrap();
    let cell = cells.iter().find(|c| c.row == 1 && c.col == 0).unwrap();
    assert_eq!(cell.link.as_deref(), Some("https://example.com/sheet"));
}
