import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
public class Grid2D extends Canvas implements KeyListener, MouseListener, MouseMotionListener, Runnable{
    static final int RES = 1; //1=160x120, 2=320x240, 4=640x480
    static final int SCREEN_WIDTH = 160*RES;
    static final int SCREEN_HEIGHT = 120*RES;
    static final int PIXEL_SCALE = 4/RES; //swing pixel scale
    static final int SWING_SCREEN_WIDTH = SCREEN_WIDTH*PIXEL_SCALE;
    static final int SWING_SCREEN_HEIGHT = SCREEN_HEIGHT*PIXEL_SCALE;
    static final int FRAMES_PER_SECOND = 20;
    static final int PLAYER_TURN_SPEED = 4;
    static final int PLAYER_MOVE_SPEED = 12;
    int[] T_NUMBERS;
    int[] T_VIEW2D;
    final double[] cos = new double[360];
    final double[] sin = new double[360];
    //number of textures
    int numText=0;
    //number of sectors
    int numSect=0;
    //number of walls
    int numWall=0;
    Keys keys;
    Player player;
    Grid grid;
    Wall[] walls;
    Sector[] sectors;
    TextureMap[] textures;
    public Grid2D() {
        init();
        initGlobals();

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        new Thread(this).start();
        setVisible(true);
    }
    void init() {
        keys=new Keys();
        grid=new Grid();
        walls=new Wall[256];
        for(int i=0;i<walls.length;i++){walls[i] = new Wall();}
        sectors=new Sector[128];
        for(int i=0;i<sectors.length;i++){sectors[i] = new Sector();}
        textures=new TextureMap[64];
        for(int x = 0; x < 360; x++) {
            sin[x] = Math.sin(x/180.0*Math.PI);
            cos[x] = Math.cos(x/180.0*Math.PI);
        }
        //add textures
        ArrayList<String> textureNames = new ArrayList<>();
        ArrayList<int[]> tempTextures = new ArrayList<>();
        String folderPath = "textures";
        File folder = new File(folderPath);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            assert files != null;
            for (File file : files) {
                if (file.isFile()) {
                    try {
                        if(file.getName().equals("T_NUMBERS.txt")) {
                            int[] t = txtToArray(file.getAbsolutePath());
                            T_NUMBERS = Arrays.copyOfRange(t, 2, t.length);
                        }else if(file.getName().equals("T_VIEW2D.txt")) {
                            int[] t = txtToArray(file.getAbsolutePath());
                            T_VIEW2D = Arrays.copyOfRange(t, 2, t.length);
                        }else {
                            tempTextures.add(txtToArray(file.getAbsolutePath()));
                            textureNames.add(file.getName().replaceAll(".txt", ""));
                        }
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        //don't take NUMBERS or VIEW2D into account
        numText = tempTextures.size()-1;
        for(int i=0;i<numText+1;i++) {
            //h, w, name, texture
            textures[i] = new TextureMap(
                    tempTextures.get(i)[0], tempTextures.get(i)[1],
                    textureNames.get(i),
                    Arrays.copyOfRange(tempTextures.get(i), 2, tempTextures.get(i).length));
        }



        player = new Player(32*9, 48, 30, 0, 0);
    }
    void initGlobals() {
        //scale down grid
        grid.scale=4;
        //select sector, walls
        grid.selS=0;grid.selW=0;
        //sector bottom top height
        grid.z1=0;grid.z2=40;
        //sector texture, scale
        grid.st=1;grid.ss=4;
        //wall texture, u,v
        grid.wt=0;grid.wu=1;grid.wv=1;
    }
    void save() {
        if (numSect == 0) {return;}
        try (PrintWriter writer = new PrintWriter(new FileWriter("level.txt"))) {
            writer.println(numSect);
            for (int s = 0; s < numSect; s++) {
                writer.printf("%d %d %d %d %d %d%n",
                        sectors[s].ws,sectors[s].we,sectors[s].z1,sectors[s].z2,sectors[s].st,sectors[s].ss);
            }

            writer.println(numWall);
            for (int w = 0; w < numWall; w++) {
                writer.printf("%d %d %d %d %d %d %d %d%n",
                        walls[w].x1,walls[w].y1,walls[w].x2,walls[w].y2,walls[w].wt,walls[w].u,walls[w].v,walls[w].shade);
            }
            writer.printf("%n%d %d %d %d %d", player.x, player.y, player.z, player.a, player.l);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    void load() {
        try (Scanner scanner = new Scanner(new FileReader("level.txt"))) {
            numSect = scanner.nextInt();
            for (int s = 0; s < numSect; s++) {
                sectors[s].ws = scanner.nextInt();
                sectors[s].we = scanner.nextInt();
                sectors[s].z1 = scanner.nextInt();
                sectors[s].z2 = scanner.nextInt();
                sectors[s].st = scanner.nextInt();
                sectors[s].ss = scanner.nextInt();
            }
            numWall = scanner.nextInt();
            for (int w = 0; w < numWall; w++) {
                walls[w].x1 = scanner.nextInt();
                walls[w].y1 = scanner.nextInt();
                walls[w].x2 = scanner.nextInt();
                walls[w].y2 = scanner.nextInt();
                walls[w].wt = scanner.nextInt();
                walls[w].u = scanner.nextInt();
                walls[w].v = scanner.nextInt();
                walls[w].shade = scanner.nextInt();
            }
            player.x = scanner.nextInt();
            player.y = scanner.nextInt();
            player.z = scanner.nextInt();
            player.a = scanner.nextInt();
            player.l = scanner.nextInt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(Graphics g) {
        display(g);
    }

    BufferedImage back;
    void display(Graphics g) {
        if(back == null) back = (BufferedImage)(createImage(getWidth(), getHeight()));
        Graphics2D g2D = back.createGraphics();

        movePlayer();
        draw2D(g2D);
        darken(g2D);

        ((Graphics2D)g).drawImage(back, null, 0, 0);
    }
    void drawPixel(Graphics2D g2D, int x,int y, int r,int g,int b)
    {
        g2D.setColor(new Color(r, g, b));
        g2D.fill(new Rectangle2D.Double(x*PIXEL_SCALE, SWING_SCREEN_HEIGHT-PIXEL_SCALE-(y*PIXEL_SCALE),
                PIXEL_SCALE, PIXEL_SCALE));
    }

    void drawLine(Graphics2D g2D, double x1, double y1, double x2, double y2, int r, int g, int b) {
        double x = x2-x1;
        double y = y2-y1;
        double max=Math.abs(x); if(Math.abs(y)>max) {max=Math.abs(y);}
        x /= max; y /= max;
        for(int n=0;n<max;n++){
            drawPixel(g2D,(int)x1,(int)y1,r,g,b);
            x1+=x;y1+=y;
        }
    }

    void drawNumber(Graphics2D g2D, int nx, int ny, int n) {
        for(int y=0;y<5;y++){
            int y2=((5-y-1)+5*n)*3*12;
            for(int x=0;x<12;x++){
                int x2=x*3;
                if(T_NUMBERS[y2+x2]==0){continue;}
                drawPixel(g2D,x+nx,y+ny,255,255,255);
            }
        }
    }
    void draw2D(Graphics2D g2D) {
        //draw background image
        for(int y=0;y<120;y++)
        {
            //invert height, x3 for rgb, x15 for texture width
            int y2=(SCREEN_HEIGHT-y-1)*3*160;
            for(int x=0;x<160;x++)
            {
                int pixel=x*3+y2;
                int r=T_VIEW2D[pixel+0];
                int g=T_VIEW2D[pixel+1];
                int b=T_VIEW2D[pixel+2];
                //darken sector button
                if(grid.addSect>0 && y>48-8 && y<56-8 && x>144){r=r>>1;g=g>>1;b=b>>1;}
                drawPixel(g2D,x,y,r,g,b);
            }
        }

        //draw sectors
        int c;
        for(int s=0;s<numSect;s++)
        {
            for(int w=sectors[s].ws;w<sectors[s].we;w++)
            {
                if(s==grid.selS-1) //if this sector is selected
                {
                    //set sector to globals
                    sectors[grid.selS-1].z1=grid.z1;
                    sectors[grid.selS-1].z2=grid.z2;
                    sectors[grid.selS-1].st=grid.st;
                    sectors[grid.selS-1].ss=grid.ss;
                    //yellow select
                    if(grid.selW==0){
                        //all walls yellow
                        c=80;
                    }
                    else if(grid.selW+sectors[s].ws-1==w){
                        //one wall selected
                        c=80;
                        walls[w].wt=grid.wt;
                        walls[w].u=grid.wu;
                        walls[w].v=grid.wv;
                    }else{
                        //grey walls
                        c= 0;
                    }
                }else{
                    //sector not selected, grey
                    c=0;
                }

                drawLine(g2D, walls[w].x1/grid.scale,walls[w].y1/grid.scale,
                        walls[w].x2/grid.scale,walls[w].y2/grid.scale,128+c,128+c,128-c);
                drawPixel(g2D, walls[w].x1/grid.scale,walls[w].y1/grid.scale,255,255,255);
                drawPixel(g2D, walls[w].x2/grid.scale,walls[w].y2/grid.scale,255,255,255);
            }
        }

        //draw player
        int dx=(int)(sin[player.a]*PLAYER_MOVE_SPEED);
        int dy=(int)(cos[player.a]*PLAYER_MOVE_SPEED);
        drawPixel(g2D, player.x/grid.scale,player.y/grid.scale,0,255,0);
        drawPixel(g2D, (player.x+dx)/grid.scale,(player.y+dy)/grid.scale,0,175,0);

        //draw wall texture
        double tx=0, tx_stp=textures[grid.wt].w/15.0;
        double ty=0, ty_stp=textures[grid.wt].h/15.0;
        for(int y=0;y<15;y++)
        {
            tx=0;
            for(int x=0;x<15;x++)
            {
                int x2=(int)tx%textures[grid.wt].w; tx+=tx_stp;//*grid.wu;
                int y2=(int)ty%textures[grid.wt].h;
                int r=textures[grid.wt].texture[(textures[grid.wt].h-y2-1)*3*textures[grid.wt].w+x2*3+0];
                int g=textures[grid.wt].texture[(textures[grid.wt].h-y2-1)*3*textures[grid.wt].w+x2*3+1];
                int b=textures[grid.wt].texture[(textures[grid.wt].h-y2-1)*3*textures[grid.wt].w+x2*3+2];
                drawPixel(g2D, x+145,y+105-8,r,g,b);
            }
            ty+=ty_stp;//*grid.wv;
        }
        //draw surface texture
        tx=0;tx_stp=textures[grid.st].w/15.0;
        ty=0;ty_stp=textures[grid.st].h/15.0;
        for(int y=0;y<15;y++)
        {
            tx=0;
            for(int x=0;x<15;x++)
            {
                int x2=(int)tx%textures[grid.st].w; tx+=tx_stp;//*grid.ss;
                int y2=(int)ty%textures[grid.st].h;
                int r=textures[grid.st].texture[(textures[grid.st].h-y2-1)*3*textures[grid.st].w+x2*3+0];
                int g=textures[grid.st].texture[(textures[grid.st].h-y2-1)*3*textures[grid.st].w+x2*3+1];
                int b=textures[grid.st].texture[(textures[grid.st].h-y2-1)*3*textures[grid.st].w+x2*3+2];
                drawPixel(g2D, x+145,y+105-24-8,r,g,b);
            }
            ty+=ty_stp;//*grid.ss;
        }
        //draw numbers
        drawNumber(g2D, 140,90,grid.wu);   //wall u
        drawNumber(g2D, 148,90,grid.wv);   //wall v
        drawNumber(g2D, 148,66,grid.ss);   //surface v
        drawNumber(g2D, 148,58,grid.z2);   //top height
        drawNumber(g2D, 148,50,grid.z1);   //bottom height
        drawNumber(g2D, 148,26,grid.selS); //sector number
        drawNumber(g2D, 148,18,grid.selW); //wall number
    }

    //darken buttons
    int dark=0;
    void darken(Graphics2D g2D)
    {
        //draw a pixel at x/y with rgb
        int xs=0,xe=0,ys=0,ye=0;
        if(dark== 0){ return;}             //no buttons were clicked
        if(dark== 1){ xs= 0; xe=15; ys=  0/grid.scale; ye= 32/grid.scale;} //save button
        if(dark== 2){ xs= 0; xe= 3; ys= 96/grid.scale; ye=128/grid.scale;} //u left
        if(dark== 3){ xs= 4; xe= 8; ys= 96/grid.scale; ye=128/grid.scale;} //u right
        if(dark== 4){ xs= 7; xe=11; ys= 96/grid.scale; ye=128/grid.scale;} //v left
        if(dark== 5){ xs=11; xe=15; ys= 96/grid.scale; ye=128/grid.scale;} //u right
        if(dark== 6){ xs= 0; xe= 8; ys=192/grid.scale; ye=224/grid.scale;} //u left
        if(dark== 7){ xs= 8; xe=15; ys=192/grid.scale; ye=224/grid.scale;} //u right
        if(dark== 8){ xs= 0; xe= 7; ys=224/grid.scale; ye=256/grid.scale;} //Top left
        if(dark== 9){ xs= 7; xe=15; ys=224/grid.scale; ye=256/grid.scale;} //Top right
        if(dark==10){ xs= 0; xe= 7; ys=256/grid.scale; ye=288/grid.scale;} //Bot left
        if(dark==11){ xs= 7; xe=15; ys=256/grid.scale; ye=288/grid.scale;} //Bot right
        if(dark==12){ xs= 0; xe= 7; ys=352/grid.scale; ye=386/grid.scale;} //sector left
        if(dark==13){ xs= 7; xe=15; ys=352/grid.scale; ye=386/grid.scale;} //sector right
        if(dark==14){ xs= 0; xe= 7; ys=386/grid.scale; ye=416/grid.scale;} //wall left
        if(dark==15){ xs= 7; xe=15; ys=386/grid.scale; ye=416/grid.scale;} //wall right
        if(dark==16){ xs= 0; xe=15; ys=416/grid.scale; ye=448/grid.scale;} //delete
        if(dark==17){ xs= 0; xe=15; ys=448/grid.scale; ye=480/grid.scale;} //load

        for(int y=ys;y<ye;y++)
        {
            for(int x=xs;x<xe;x++)
            {
                //40% transparency
                g2D.setColor(new Color(0, 0, 0, 102));
                g2D.fill(new Rectangle2D.Double(
                        x*PIXEL_SCALE+580, SWING_SCREEN_HEIGHT-((120-y)*PIXEL_SCALE),
                        PIXEL_SCALE, PIXEL_SCALE));
            }
        }
    }

    void movePlayer() {
        //turn left and right
        if (!keys.m && keys.a) {player.a-=PLAYER_TURN_SPEED; if(player.a<0) {player.a+=360;}}
        if (!keys.m && keys.d) {player.a+=PLAYER_TURN_SPEED; if(player.a>359) {player.a-=360;}}
        //move forwards and backwards
        int deltaX = (int)(sin[player.a]*PLAYER_MOVE_SPEED);
        int deltaY = (int)(cos[player.a]*PLAYER_MOVE_SPEED);
        if (!keys.m && keys.w) {player.x+=deltaX;player.y+=deltaY;}
        if (!keys.m && keys.s) {player.x-=deltaX;player.y-=deltaY;}
        //strafe left and right
        if (keys.sl) {player.x-=deltaY;player.y+=deltaX;}
        if (keys.sr) {player.x+=deltaY;player.y-=deltaX;}
        //move up and down
        if (keys.m && keys.w) {player.z+=4;}
        if (keys.m && keys.s) {player.z-=4;}
        //look up and down
        if (keys.m && keys.a) {player.l-=1;}
        if (keys.m && keys.d) {player.l+=1;}
    }
    int[] txtToArray(String filePath) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            int height = Integer.parseInt(reader.readLine().trim());
            int width = Integer.parseInt(reader.readLine().trim());
            int[] intArray = new int[height*3*width+2];
            intArray[0]=height;
            intArray[1]=width;
            for (int h = 0; h < height*3; h++) {
                int[] temp = strToIntArray(reader.readLine().trim().split(" "));
                for(int w=0; w < width; w++) {
                    intArray[h*width+w+2] = temp[w];
                }
            }
            reader.close();
            return intArray;
        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public int[] strToIntArray(String[] array) {
        int[] intArray = new int[array.length];
        for(int i = 0; i < array.length; i++){
            intArray[i] = Integer.parseInt(array[i]);
        }
        return intArray;
    }
    //key listener
    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_A) {keys.a = true;}
        if (e.getKeyCode() == KeyEvent.VK_D) {keys.d = true;}
        if (e.getKeyCode() == KeyEvent.VK_W) {keys.w = true;}
        if (e.getKeyCode() == KeyEvent.VK_S) {keys.s = true;}
        if (e.getKeyCode() == KeyEvent.VK_M) {keys.m = true;}
        if (e.getKeyCode() == KeyEvent.VK_COMMA) {keys.sl = true;}
        if (e.getKeyCode() == KeyEvent.VK_PERIOD) {keys.sr = true;}
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_A) {keys.a = false;}
        if (e.getKeyCode() == KeyEvent.VK_D) {keys.d = false;}
        if (e.getKeyCode() == KeyEvent.VK_W) {keys.w = false;}
        if (e.getKeyCode() == KeyEvent.VK_S) {keys.s = false;}
        if (e.getKeyCode() == KeyEvent.VK_M) {keys.m = false;}
        if (e.getKeyCode() == KeyEvent.VK_COMMA) {keys.sl = false;}
        if (e.getKeyCode() == KeyEvent.VK_PERIOD) {keys.sr = false;}
    }
    //mouse listener
    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();

        //round mouse x,y
        grid.mx=x/PIXEL_SCALE;
        grid.my=SCREEN_HEIGHT-y/PIXEL_SCALE;
        grid.mx=((grid.mx+4)>>3)<<3;
        grid.my=((grid.my+4)>>3)<<3; //nearest 8th

        if(e.getButton() == MouseEvent.BUTTON1)
        {
            //2D view buttons only
            if(x>580)
            {
                //2d 3d view buttons

                //save
                if(y>0 && y<32){
                    save();
                    dark=1;
                }
                //wall texture
                if(y>32 && y<96){
                    if(x<610){
                        grid.wt-=1;
                        if(grid.wt<0){
                            grid.wt=numText;
                        }
                    }else {
                        grid.wt+=1;
                        if(grid.wt>numText){
                            grid.wt=0;
                        }
                    }
                }
                //wall uv
                if(y>96 && y<128)
                {
                    if(x<595){ dark=2; grid.wu-=1; if(grid.wu< 1){ grid.wu= 1;}}
                    else if(x<610){ dark=3; grid.wu+=1; if(grid.wu> 9){ grid.wu= 9;}}
                    else if(x<625){ dark=4; grid.wv-=1; if(grid.wv< 1){ grid.wv= 1;}}
                    else if(x<640){ dark=5; grid.wv+=1; if(grid.wv> 9){ grid.wv= 9;}}
                }
                //surface texture
                if(y>128 && y<192){
                    if(x<610){
                        grid.st-=1;
                        if(grid.st<0){
                            grid.st=numText;
                        }
                    }else{
                        grid.st+=1;
                        if(grid.st>numText){
                            grid.st=0;
                        }
                    }
                }
                //surface uv
                if(y>192 && y<222)
                {
                    if(x<610){
                        dark=6; grid.ss-=1;
                        if(grid.ss< 1){ grid.ss= 1;}
                    }else{
                        dark=7; grid.ss+=1;
                        if(grid.ss> 9){ grid.ss= 9;}
                    }
                }
                //top height
                if(y>222 && y<256)
                {
                    if(x<610){
                        dark=8; grid.z2-=5;
                        if(grid.z2==grid.z1){ grid.z1-=5;}
                    }else{ dark=9; grid.z2+=5;}
                }
                //bot height
                if(y>256 && y<288)
                {
                    if(x<610){
                        dark=10;
                        grid.z1-=5;
                    }else{
                        dark=11;
                        grid.z1+=5;
                        if(grid.z1==grid.z2){
                            grid.z2+=5;
                        }
                    }
                }
                //add sector
                if(y>288 && y<318){
                    grid.addSect+=1;
                    grid.selS=0;
                    grid.selW=0;
                    if(grid.addSect>1){
                        grid.addSect=0;}
                }
                //limit
                if(grid.z1<0){ grid.z1=0;}
                if(grid.z1>145){ grid.z1=145;}
                if(grid.z2<5){ grid.z2=5;}
                if(grid.z2>150){ grid.z2=150;}

                //select sector
                if(y>352 && y<386)
                {
                    grid.selW=0;
                    if(x<610){
                        dark=12;
                        grid.selS-=1;
                        if(grid.selS<0){
                            grid.selS=numSect;
                        }
                    }else{
                        dark=13;
                        grid.selS+=1;
                        if(grid.selS>numSect){
                            grid.selS=0;
                        }
                    }
                    int s=grid.selS-1;
                    if(grid.selS<=0){ initGlobals();} //defaults
                    else {
                        grid.z1 = sectors[s].z1; //sector bottom height
                        grid.z2 = sectors[s].z2; //sector top height
                        grid.st = sectors[s].st; //surface texture
                        grid.ss = sectors[s].ss; //surface scale
                        grid.wt = walls[sectors[s].ws].wt;
                        grid.wu = walls[sectors[s].ws].u;
                        grid.wv = walls[sectors[s].ws].v;
                    }
                }
                //select sector's walls
                int snw = 0;
                if(grid.selS>0) {
                    //sector's number of walls
                    snw = sectors[grid.selS - 1].we - sectors[grid.selS - 1].ws;
                }
                if(y>386 && y<416)
                {
                    if(x<610) //select sector wall left
                    {
                        dark=14;
                        grid.selW-=1; if(grid.selW<0){ grid.selW=snw;}
                    }
                    else //select sector wall right
                    {
                        dark=15;
                        grid.selW+=1; if(grid.selW>snw){ grid.selW=0;}
                    }
                    if(grid.selW>0)
                    {
                        grid.wt=walls[sectors[grid.selS-1].ws+grid.selW-1].wt; //printf("ws,%i,%i\n",G.wt, 1 );
                        grid.wu=walls[sectors[grid.selS-1].ws+grid.selW-1].u;
                        grid.wv=walls[sectors[grid.selS-1].ws+grid.selW-1].v;
                    }
                }
                //delete
                if(y>416 && y<448)
                {
                    dark=16;
                    if(grid.selS>0)
                    {
                        int d=grid.selS-1;                             //delete this one
                        //printf("%i before:%i,%i\n",d, numSect,numWall);
                        numWall-=(sectors[d].we-sectors[d].ws);                 //first subtract number of walls
                        for(x=d;x<numSect;x++){ sectors[x]=sectors[x+1];}       //remove from array
                        numSect-=1;                                 //1 less sector
                        grid.selS=0; grid.selW=0;                         //deselect
                        //printf("after:%i,%i\n\n",numSect,numWall);
                    }
                }

                //load
                if(y>448 && y<480){ dark=17; load();}
            }

            //clicked on grid
            else
            {
                //init new sector
                if(grid.addSect==1)
                {
                    sectors[numSect].ws=numWall;                                   //clear wall start
                    sectors[numSect].we=numWall+1;                                 //add 1 to wall end
                    sectors[numSect].z1=grid.z1;
                    sectors[numSect].z2=grid.z2;
                    sectors[numSect].st=grid.st;
                    sectors[numSect].ss=grid.ss;
                    walls[numWall].x1=grid.mx*grid.scale; walls[numWall].y1=grid.my*grid.scale;  //x1,y1
                    walls[numWall].x2=grid.mx*grid.scale; walls[numWall].y2=grid.my*grid.scale;  //x2,y2
                    walls[numWall].wt=grid.wt;
                    walls[numWall].u=grid.wu;
                    walls[numWall].v=grid.wv;
                    numWall+=1;                                              //add 1 wall
                    numSect+=1;                                              //add this sector
                    grid.addSect=3;                                             //go to point 2
                }

                //add point 2
                else if(grid.addSect==3)
                {
                    if(sectors[numSect-1].ws==numWall-1 && grid.mx*grid.scale<=walls[sectors[numSect-1].ws].x1)
                    {
                        numWall-=1; numSect-=1; grid.addSect=0;
                        System.out.println("walls must be counter clockwise\n");
                        return;
                    }

                    //point 2
                    walls[numWall-1].x2=grid.mx*grid.scale; walls[numWall-1].y2=grid.my*grid.scale; //x2,y2
                    //automatic shading
                    double ang = Math.atan2(
                            walls[numWall-1].y2-walls[numWall-1].y1,
                            walls[numWall-1].x2-walls[numWall-1].x1 );
                    ang=(ang*180)/Math.PI;      //radians to degrees
                    if(ang<0){ ang+=360;}    //correct negative
                    int shade=(int)ang;           //shading goes from 0-90-0-90-0
                    if(shade>180){ shade=180-(shade-180);}
                    if(shade> 90){ shade= 90-(shade- 90);}
                    walls[numWall-1].shade=shade;

                    //check if sector is closed
                    if(walls[numWall-1].x2==walls[sectors[numSect-1].ws].x1 &&
                            walls[numWall-1].y2==walls[sectors[numSect-1].ws].y1)
                    {
                        walls[numWall-1].wt=grid.wt;
                        walls[numWall-1].u=grid.wu;
                        walls[numWall-1].v=grid.wv;
                        grid.addSect=0;
                    }
                    //not closed, add new wall
                    else
                    {
                        //init next wall
                        sectors[numSect-1].we+=1;                                      //add 1 to wall end
                        walls[numWall].x1=grid.mx*grid.scale; walls[numWall].y1=grid.my*grid.scale;  //x1,y1
                        walls[numWall].x2=grid.mx*grid.scale; walls[numWall].y2=grid.my*grid.scale;  //x2,y2
                        walls[numWall-1].wt=grid.wt;
                        walls[numWall-1].u=grid.wu;
                        walls[numWall-1].v=grid.wv;
                        walls[numWall].shade=0;
                        numWall+=1;                                              //add 1 wall
                    }
                }
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Reset darkening when mouse is released
        if (e.getButton() == MouseEvent.BUTTON1) {
            dark = 0;
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }
    //mouse motion listener
    @Override
    public void mouseDragged(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void run() {
        try
        {
            while(true)
            {
                Thread.currentThread().sleep(1000/FRAMES_PER_SECOND);
                repaint();
            }
        }catch(Exception e)
        {
            System.out.println(e);
        }
    }
    public static void main(String[] args) {
        JFrame frame = new JFrame("Grid2D");

        frame.setSize(SWING_SCREEN_WIDTH+16, SWING_SCREEN_HEIGHT+39);

        Grid2D grid2D = new Grid2D();
        (grid2D).setFocusable(true);

        frame.getContentPane().add(grid2D);

        frame.setVisible(true);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    class Keys {
        boolean w, s, a, d, sl, sr, m;
        Keys() {}
    }
    class Player {
        //player position
        int x, y, z;
        //player angle
        int a;
        //looking up and down
        int l;
        public Player() {}
        public Player(int x, int y, int z, int a, int l) {
            this.x = x; this.y = y; this.z = z;
            this.a = a; this.l = l;
        }
    }
    class Wall {
        //bottom line point 1
        int x1, y1;
        //bottom line point 2
        int x2, y2;
        //wall texture and u/v tile
        int wt, u, v;
        //shade of wall
        int shade;
        public Wall() {}
        @Override
        public String toString() {
            return "x1:" + x1 + "; y1: " + y1 + "; x2: " + x2 + "; y2: " + y2;
        }
    }
    class Sector {
        //wall number start and end
        int ws, we;
        //height of bottom and top
        int z1, z2;
        //center positions for sector
        int d;
        //surface texture, surface scale
        int st, ss;
        //hold points for surfaces
        int[] surf = new int[SCREEN_WIDTH];
        //is there a surfaces to draw
        public Sector() {}
        public Sector(int ws, int we, int z1, int z2, int st, int ss) {
            this.ws = ws; this.we = we;
            this.z1 = z1; this.z2 = z2;
            this.st = st; this.ss = ss;
        }
        @Override
        public String toString() {
            return "ws:" + ws + "; we: " + we + "; z1: " + z1 + "; z2: " + z2;
        }
    }
    class TextureMap {
        //texture width/height
        int w, h;
        //texture name
        String name;
        //texture array
        int[] texture;
        TextureMap(){}
        TextureMap(int w, int h, String name, int[] texture) {
            this.w = w; this.h = h;
            this.name = name;
            this.texture = texture;
        }
    }
    class Grid {
        //rounded mouse position
        int mx,my;
        //0=nothing, 1=add sector
        int addSect;
        //wall    texture, uv texture tile
        int wt,wu,wv;
        //surface texture, surface scale
        int st,ss;
        //bottom and top height
        int z1,z2;
        //scale down grid
        int scale;
        //0=wall ID, 1=v1v2, 2=wallID, 3=v1v2
        int[] move;
        //select sector/wall
        int selS,selW;
        public Grid() {
            move = new int[4];
        }
    }
}