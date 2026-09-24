import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Run from the repository root with Java 17. No Android/browser pixel access needed. */
class GenerateOverlayMetrics {
  static final Path ASSETS = Path.of("android-apk/app/src/main/assets");
  static final String START = "  // BEGIN GENERATED OVERLAY METRICS\n";
  static final String END = "  // END GENERATED OVERLAY METRICS\n";
  static String bounds(BufferedImage image, int threshold) {
    int left=image.getWidth(), top=image.getHeight(), right=0, bottom=0;
    for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
      if((image.getRGB(x,y)>>>24)>threshold) {
        left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
      }
    }
    if(right<=left || bottom<=top) return null;
    return "{\"left\":"+left+",\"top\":"+top+",\"right\":"+right+",\"bottom\":"+bottom+"}";
  }
  public static void main(String[] args) throws Exception {
    List<Path> files=new ArrayList<>();
    try(var paths=Files.list(ASSETS)) {
      paths.filter(p -> {
        String name = p.getFileName().toString();
        return name.matches(".*_(snapshot|entity)_overlay\\.png") || name.equals("luctram_overlay.png");
      }).forEach(files::add);
    }
    try(var paths=Files.list(ASSETS.resolve("entity"))) {
      paths.filter(p->p.toString().endsWith(".webp")).forEach(files::add);
    }
    files.sort(Comparator.comparing(Path::toString));
    if(files.isEmpty()) throw new IllegalStateException("No overlay assets found");
    var lines=new ArrayList<String>();
    for(Path file:files) {
      BufferedImage image=ImageIO.read(file.toFile());
      if(image==null) throw new IllegalStateException("Cannot decode "+file);
      String paint=bounds(image,8),body=bounds(image,128);
      if(paint==null) throw new IllegalStateException("Empty overlay: "+file);
      if(body==null) body=paint;
      String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
      String key=ASSETS.relativize(file).toString().replace('\\','/');
      lines.add("    \""+key+"\":{\"width\":"+image.getWidth()+",\"height\":"+image.getHeight()+",\"paint\":"+paint+",\"body\":"+body+",\"sha256\":\""+hash+"\"}");
    }
    Path script=ASSETS.resolve("snapshot-ui.js");String source=Files.readString(script);
    int start=source.indexOf(START),end=source.indexOf(END);
    if(start<0 || end<start) throw new IllegalStateException("Missing metadata anchors");
    String replacement=START+"  var bundledMetrics={\n"+String.join(",\n",lines)+"\n  };\n";
    String generated=source.substring(0,start)+replacement+source.substring(end);
    if(Arrays.asList(args).contains("--check")) {
      if(!source.equals(generated)) throw new IllegalStateException("Overlay metadata stale; run java android-apk/tools/GenerateOverlayMetrics.java");
    } else Files.writeString(script,generated);
    System.out.println("Verified bounds and hashes for "+files.size()+" overlay assets");
  }
}
