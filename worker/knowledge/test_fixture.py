from pathlib import Path
import shutil
import subprocess
import sys
import zlib

def pdf(path, columns, data, text):
    def literal(s):
        return s.replace(chr(92), chr(92)*2).replace("(",chr(92)+"(").replace(")",chr(92)+")")
    all_rows=[columns,*data]
    lines=["0.75 w"]
    width=135
    end=650-30*len(all_rows)
    for x in range(len(columns)+1):
        pos=60+x*width
        lines.append(f"{pos} {end} m {pos} 650 l S")
    for index in range(len(all_rows)+1):
        y=650-index*30
        lines.append(f"60 {y} m {60+len(columns)*width} {y} l S")
    for y,row in enumerate(all_rows):
        for x,value in enumerate(row):lines.append(f"BT /F1 11 Tf {69+x*width} {630-y*30} Td ({literal(value)}) Tj ET")
    lines+=[f"BT /F1 12 Tf 60 720 Td ({literal(text)}) Tj ET"]
    other="BT /F1 12 Tf 60 720 Td (The penguin colony relies on a coastal habitat.) Tj ET"
    def stream(data):
        return f"<< /Length {len(data)} >>\nstream\n{data}\nendstream"
    objects=["<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Count 2 /Kids [3 0 R 4 0 R] >>",
     "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>",
     "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >>",
     "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",stream("\n".join(lines)),stream(other)]
    body="%PDF-1.4\n";positions=[0]
    for i,obj in enumerate(objects,1):
        positions.append(len(body));body+=f"{i} 0 obj\n{obj}\nendobj\n"
    xref=len(body)
    body+=f"xref\n0 {len(positions)}\n0000000000 65535 f \n"
    body+="".join(f"{pos:010d} 00000 n \n" for pos in positions[1:])
    body+=f"trailer\n<< /Size {len(positions)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n"
    Path(path).write_bytes(body.encode("ascii"))
pdf("target/p4-invoices.pdf",["Region","Value","Currency"],[["North","12.25","USD"],["North","4.75","USD"],["South","","USD"],["South","-2.50","USD"]],"Aster invoices in US dollars. The North branch tracks purchasing expenses.")
pdf("target/p4-mixed.pdf",["Region","Value","Currency"],[["North","12.25","USD"],["South","4.75","EUR"]],"Two different currencies are listed.")


def scanned_fixture():
    """Rasterize the first page to remove every original text operator."""
    image = subprocess.check_output(["pdftoppm", "-f", "1", "-l", "1", "-r", "110", "-singlefile",
                                     "target/p4-invoices.pdf"], stderr=subprocess.DEVNULL)
    spec, width, height, maximum, pixels = image.split(None, 4)
    assert (spec, maximum) == (b"P6", b"255")
    assert len(pixels) == int(width) * int(height) * 3
    content = b"q 612 0 0 792 0 0 cm /Image Do Q"
    pixels = zlib.compress(pixels)
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /XObject << /Image 5 0 R >> >> /Contents 4 0 R >>",
        b"<< /Length %d >>\nstream\n%s\nendstream" % (len(content), content),
        b"<< /Type /XObject /Subtype /Image /Width %s /Height %s /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length %d >>\nstream\n%s\nendstream"
        % (width, height, len(pixels), pixels),
    ]
    output = bytearray(b"%PDF-1.4\n")
    starts = [0]
    for index, body in enumerate(objects, 1):
        starts.append(len(output))
        output.extend(b"%d 0 obj\n%s\nendobj\n" % (index, body))
    xref = len(output)
    output.extend(b"xref\n0 %d\n0000000000 65535 f \n" % len(starts))
    output.extend(b"".join(b"%010d 00000 n \n" % start for start in starts[1:]))
    output.extend(b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(starts), xref))
    Path("target/p4-scan.pdf").write_bytes(output)


if "--scan" in sys.argv:
    if shutil.which("pdftoppm") is None:
        raise SystemExit("Install pdftoppm for the optional raster fixture.")
    scanned_fixture()
