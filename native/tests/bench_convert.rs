//! Single-core throughput sanity check for the conversion kernel (run with --ignored).

use dreamdisplays_native::convert;

#[test]
#[ignore]
fn bench_nv12_1080p() {
    let (w, h) = (1920usize, 1080usize);
    let raw = vec![128u8; convert::nv12_frame_size(w, h)];
    let mut dst = vec![0u8; w * h * 3];
    let lut = convert::build_lut(900);
    for _ in 0..10 {
        convert::nv12_to_rgb24(&raw, w, h, &mut dst, &lut);
    }
    let n = 200;
    let t = std::time::Instant::now();
    for _ in 0..n {
        convert::nv12_to_rgb24(&raw, w, h, &mut dst, &lut);
    }
    let per = t.elapsed() / n;
    println!(
        "NV12 -> RGB24 + brightness 1080p: {:?} / frame ({:.0} fps single core).",
        per,
        1.0 / per.as_secs_f64()
    );
}
