import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class Doom extends Canvas implements KeyListener, MouseMotionListener, Runnable {
    static final int RES = 1; //1=160x120, 2=320x240, 4=640x480
    static final int SCREEN_WIDTH = 160*RES;
    static final int SCREEN_HEIGHT = 120*RES;
    static final int PIXEL_SCALE = 4/RES; //swing pixel scale
    static final int SWING_SCREEN_WIDTH = SCREEN_WIDTH*PIXEL_SCALE;
    static final int SWING_SCREEN_HEIGHT = SCREEN_HEIGHT*PIXEL_SCALE;
    static final int FRAMES_PER_SECOND = 20;
    static final double PLAYER_TURN_SPEED = 2.0;
    static final double PLAYER_MOVE_SPEED = 10.0;
    final double[] cos = new double[360];
    final double[] sin = new double[360];
    Robot robot;
    boolean isCursorConfined = true;
    Cursor invisibleCursor;
    int numText;
    int numSect;
    int numWall;
    Keys keys;
    Player player;
    Wall[] walls;
    Sector[] sectors;
    TextureMap[] textures;
    MouseMovement mouse;
    Doom() {
        addKeyListener(this);
        addMouseMotionListener(this);
        setVisible(true);
        setFocusable(true);
        requestFocusInWindow();
        setPreferredSize(new Dimension(SWING_SCREEN_WIDTH, SWING_SCREEN_HEIGHT));

        init();

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image image = toolkit.createImage(new byte[0]);
        invisibleCursor = toolkit.createCustomCursor(image, new Point(0, 0), "InvisibleCursor");
        setCursor(invisibleCursor);

        try {
            robot = new Robot();
        } catch (AWTException ex) {
            ex.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            Runnable mouseConfineTask = new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (isCursorConfined && isShowing()) {
                            Point location = MouseInfo.getPointerInfo().getLocation();

                            // Get the bounds of the panel in screen coordinates
                            Rectangle bounds = getBounds();
                            Point panelLocation = getLocationOnScreen();
                            bounds.translate(panelLocation.x, panelLocation.y);

                            int mouseX = location.x;
                            int mouseY = location.y;

                            boolean moved = false;

                            // Check if the mouse is at the edge and move to the opposite side
                            if (mouseX <= bounds.x) {
                                mouseX = bounds.x + bounds.width - 1;
                                moved = true;
                            } else if (mouseX >= bounds.x + bounds.width - 1) {
                                mouseX = bounds.x;
                                moved = true;
                            }

                            if (mouseY <= bounds.y) {
                                mouseY = bounds.y + bounds.height - 1;
                                moved = true;
                            } else if (mouseY >= bounds.y + bounds.height - 1) {
                                mouseY = bounds.y;
                                moved = true;
                            }

                            if (moved) {
                                // Move the mouse cursor to the new location
                                robot.mouseMove(mouseX, mouseY);
                            }
                            mouse.xOld = mouse.xNew;
                            mouse.yOld = mouse.yNew;
                            mouse.xNew = mouseX;
                            mouse.yNew = mouseY;
                        }

                        try {
                            // Sleep for a short period to reduce CPU usage
                            Thread.sleep(10);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            };

            Thread mouseConfineThread = new Thread(mouseConfineTask);
            mouseConfineThread.start();
        });

        new Thread(this).start();
    }
    void init() {
        keys = new Keys();
        walls = new Wall[1000];
        mouse = new MouseMovement();
        for(int i=0;i<walls.length;i++){walls[i] = new Wall();}
        sectors = new Sector[1000];
        for(int i=0;i<sectors.length;i++){sectors[i] = new Sector();}
        textures = new TextureMap[64];
        for(int i=0;i<textures.length;i++){textures[i] = new TextureMap();}

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
                        if(!file.getName().equals("T_NUMBERS.txt") && !file.getName().equals("T_VIEW2D.txt")) {
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

        player = new Player(70, -110, 20, 0, 0);
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
        movePlayer();
    }

    BufferedImage back;
    void display(Graphics g) {
        if(back == null) back = (BufferedImage)(createImage(getWidth(), getHeight()));
        Graphics2D g2D = back.createGraphics();

        clearBackground(g2D);
        draw3D(g2D);
        //floors(g2D);

        ((Graphics2D)g).drawImage(back, null, 0, 0);
    }
    int[] clipBehindPlayer(int x1, int y1, int z1, int x2, int y2, int z2) {
        double da=y1;
        double db=y2;
        double d=da-db; if(d==0) {d=1;}
        double s = da/(da-db);
        x1 += s*(x2-x1);
        y1 += s*(y2-y1); if(y1==0) {y1=1;}
        z1 += s*(z2-z1);
        return new int[]{x1, y1, z1};
    }
    void drawWall(Graphics2D g2D, int x1,int x2,int b1,int b2,
                  int t1,int t2,int s,int w,int frontBack)
    {
        //wall texture
        int wt=walls[w].wt;
        //horizontal wall texture starting and step value
        double ht=0, ht_step=(double)textures[wt].w*walls[w].u/(double)(x2-x1);

        //Hold difference in distance
        int dyb  = b2-b1;                       //y distance of bottom line
        int dyt  = t2-t1;                       //y distance of top    line
        int dx   = x2-x1; if( dx==0){ dx=1;}    //x distance
        int xs=x1;                              //hold initial x1 starting position
        //CLIP X
        if(x1<0){ ht-=ht_step*x1; x1=0;} //clip left
        if(x2<0){ x2=0;} //clip left
        if(x1>SCREEN_WIDTH){ x1=SCREEN_WIDTH;} //clip right
        if(x2>SCREEN_WIDTH){ x2=SCREEN_WIDTH;} //clip right
        //draw x verticle lines
        for(int x=x1;x<x2;x++)
        {//The Y start and end point
            int y1 = (int)(dyb*(x-xs+0.5)/dx+b1); //y bottom point
            int y2 = (int)(dyt*(x-xs+0.5)/dx+t1); //y bottom point

            //vertical wall texture starting and step value
            double vt=0, vt_step=(double)textures[wt].h*walls[w].v/(double)(y2-y1);
            //Clip Y
            if(y1<0){ vt-=vt_step*y1; y1=0;} //clip y
            if(y2<0){ y2=0;} //clip y
            if(y1>SCREEN_HEIGHT){ y1=SCREEN_HEIGHT;} //clip y
            if(y2>SCREEN_HEIGHT){ y2=SCREEN_HEIGHT;} //clip y
            //draw front wall
            if(frontBack==0)
            {
                if(sectors[s].surface==1) {sectors[s].surf[x]=y1;} //bottom surface save top row
                if(sectors[s].surface==2) {sectors[s].surf[x]=y2;} //top surface save top row
                for(int y=y1;y<y2;y++) //normal wall
                {
                    int pixel=(int)(textures[wt].h-((int)vt%textures[wt].h)-1)*3*textures[wt].w +
                            ((int)ht%textures[wt].w)*3;
                    int r=textures[wt].texture[pixel+0]-walls[w].shade/2; if(r<0) {r=0;}
                    int g=textures[wt].texture[pixel+1]-walls[w].shade/2; if(g<0) {g=0;}
                    int b=textures[wt].texture[pixel+2]-walls[w].shade/2; if(b<0) {b=0;}
                    drawPixel(g2D, x,y,r,g,b);
                    vt+=vt_step;
                }
                ht+=ht_step;
            }
            //draw back wall and surface
            if(frontBack==1){
                int xo=SCREEN_WIDTH/2;//x offset
                int yo=SCREEN_HEIGHT/2;//y offset
                double fov=200;//field of view
                int x3=x-xo;//x - x offset
                int wo=0;//wall offset
                float tile=sectors[s].ss*7; //imported surface tile

                if(sectors[s].surface==1) {y2=sectors[s].surf[x]; wo=sectors[s].z1;}   //bottom surface
                if(sectors[s].surface==2) {y1=sectors[s].surf[x]; wo=sectors[s].z2;}   //top surface

                double lookUpDown=-player.l*6.2;
                //if(lookUpDown>SCREEN_HEIGHT) {lookUpDown=SCREEN_HEIGHT;}
                double moveUpDown=(double)(player.z-wo)/(double)yo;
                if(moveUpDown==0) { moveUpDown=0.001;}
                int ys=y1-yo, ye=y2-yo; //y start and y end

                for(int y=ys;y<ye;y++)
                {
                    double  z = y+lookUpDown; if(z==0) { z=0.0001;}
                    //world floor x
                    double fx = x3 /z*moveUpDown*tile;
                    //world floor y
                    double fy = fov/z*moveUpDown*tile;
                    //rotated texture x
                    double rx=fx*sin[player.a]-fy*cos[player.a]+(player.y/(double)yo*tile);
                    //rotated texture y
                    double ry=fx*cos[player.a]+fy*sin[player.a]-(player.x/(double)yo*tile);
                    //remove negative values
                    if(rx<0) { rx=-rx+1;}
                    if(ry<0) { ry=-ry+1;}
                    //surface texture
                    int st=sectors[s].st;
                    int pixel=(int)(textures[st].h-((int)ry%textures[st].h)-1)*3*textures[st].w +
                            ((int)rx%textures[st].w)*3;
                    int r=textures[st].texture[pixel+0];
                    int g=textures[st].texture[pixel+1];
                    int b=textures[st].texture[pixel+2];
                    drawPixel(g2D,x3+xo,y+yo,r,g,b);
                }
            }
        }
    }
    int dist(int x1, int y1, int x2, int y2) {
        return (int)(Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1)));
    }
    void draw3D(Graphics2D g2D) {
        int frontBack, cycles;
        //world x, y, z
        int[] wx=new int[4], wy=new int[4],wz=new int[4];
        double CS=cos[player.a], SN=sin[player.a];
        //order sectors by distance
        for(int s=0;s<numSect-1;s++)
        {
            for(int w=0;w<numSect-s-1;w++)
            {
                if(sectors[w].d<sectors[w+1].d)
                {
                    Sector st=sectors[w]; sectors[w]=sectors[w+1]; sectors[w+1]=st;
                }
            }
        }

        //draw sectors
        for(int s=0;s<numSect;s++) {
            //clear distance
            sectors[s].d=0;

            //bottom surface
            if(player.z<sectors[s].z1){
                sectors[s].surface=1;
                cycles=2;
                for(int x=0; x<SCREEN_WIDTH;x++){
                    sectors[s].surf[x]=SCREEN_HEIGHT;
                }
            }

            //top surface
            else if(player.z>sectors[s].z2){
                sectors[s].surface=2;
                cycles=2;
                for(int x=0; x<SCREEN_WIDTH;x++) {
                    sectors[s].surf[x]= 0;
                }
            }
            //no surfaces
            else{
                sectors[s].surface=0; cycles=1;
            }
            for(frontBack=0;frontBack<cycles;frontBack++)
            {
                for(int w=sectors[s].ws;w<sectors[s].we;w++)
                {
                    //offset bottom 2 points by player
                    int x1=walls[w].x1-player.x, y1=walls[w].y1-player.y;
                    int x2=walls[w].x2-player.x, y2=walls[w].y2-player.y;
                    //swap for surface
                    if(frontBack==1){ int swp=x1; x1=x2; x2=swp; swp=y1; y1=y2; y2=swp;}
                    //world X position
                    wx[0]=(int)(x1*CS-y1*SN);
                    wx[1]=(int)(x2*CS-y2*SN);
                    wx[2]=wx[0];                          //top line has the same x
                    wx[3]=wx[1];
                    //world Y position (depth)
                    wy[0]=(int)(y1*CS+x1*SN);
                    wy[1]=(int)(y2*CS+x2*SN);
                    wy[2]=wy[0];                          //top line has the same y
                    wy[3]=wy[1];
                    sectors[s].d+=dist(0,0, (wx[0]+wx[1])/2, (wy[0]+wy[1])/2);  //store this wall distance
                    //world z height
                    wz[0]=(int)(sectors[s].z1-player.z+((player.l*wy[0])/32.0));
                    wz[1]=(int)(sectors[s].z1-player.z+((player.l*wy[1])/32.0));
                    wz[2]=(int)(sectors[s].z2-player.z+((player.l*wy[0])/32.0));                       //top line has new z
                    wz[3]=(int)(sectors[s].z2-player.z+((player.l*wy[1])/32.0));
                    //dont draw if behind player
                    if(wy[0]<1 && wy[1]<1) {continue;}
                    //point 1 behind player, clip
                    if(wy[0]<1) {
                        int[] temp;
                        temp = clipBehindPlayer(wx[0], wy[0], wz[0], wx[1], wy[1], wz[1]);
                        wx[0] = temp[0]; wy[0] = temp[1]; wz[0] = temp[2];
                        temp = clipBehindPlayer(wx[2], wy[2], wz[2], wx[3], wy[3], wz[3]);
                        wx[2] = temp[0]; wy[2] = temp[1]; wz[2] = temp[2];
                    }
                    //point 2 behind player, clip
                    if(wy[1]<1) {
                        int[] temp;
                        temp = clipBehindPlayer(wx[1], wy[1], wz[1], wx[0], wy[0], wz[0]);
                        wx[1] = temp[0]; wy[1] = temp[1]; wz[1] = temp[2];
                        temp = clipBehindPlayer(wx[3], wy[3], wz[3], wx[2], wy[2], wz[2]);
                        wx[3] = temp[0]; wy[3] = temp[1]; wz[3] = temp[2];
                    }
                    //screen x, screen y position
                    wx[0]=wx[0]*200/wy[0]+(SCREEN_WIDTH/2); wy[0]=wz[0]*200/wy[0]+(SCREEN_HEIGHT/2);
                    wx[1]=wx[1]*200/wy[1]+(SCREEN_WIDTH/2); wy[1]=wz[1]*200/wy[1]+(SCREEN_HEIGHT/2);
                    wx[2]=wx[2]*200/wy[2]+(SCREEN_WIDTH/2); wy[2]=wz[2]*200/wy[2]+(SCREEN_HEIGHT/2);
                    wx[3]=wx[3]*200/wy[3]+(SCREEN_WIDTH/2); wy[3]=wz[3]*200/wy[3]+(SCREEN_HEIGHT/2);
                    //draw points
                    drawWall(g2D, wx[0],wx[1], wy[0],wy[1], wy[2],wy[3], s, w, frontBack);
                }
                //find average sector distance
                sectors[s].d/=( sectors[s].we- sectors[s].ws);
            }
        }
    }

    void floors(Graphics2D g2D) {
        int x,y;
        int xo=SCREEN_WIDTH/2; //x offset
        int yo=SCREEN_HEIGHT/2; //y offset
        double fov=200;
        double lookUpDown=-player.l*2; if(lookUpDown>SCREEN_HEIGHT) { lookUpDown=SCREEN_HEIGHT;}
        double moveUpDown=player.z/16.0; if(moveUpDown==0) { moveUpDown=0.001;}
        int ys=-yo, ye=(int)(-lookUpDown);
        if(moveUpDown<0) { ys=(int)(-lookUpDown); ye=(int)(yo+lookUpDown);}

        for(y=ys;y<ye;y++)
        {
            for(x=-xo;x<xo;x++)
            {
                double z = y+lookUpDown; if(z==0) { z=0.0001;}
                double fx = x/z*moveUpDown;                      //world floor x
                double fy = fov/z*moveUpDown;                      //world floor y
                double rx=fx*sin[player.a]-fy*cos[player.a]+(player.y/30.0);  //rotated texture x
                double ry=fx*cos[player.a]+fy*sin[player.a]-(player.x/30.0);  //rotated texture y
                if(rx<0) { rx=-rx+1;}                             //remove negative values
                if(ry<0) { ry=-ry+1;}                             //remove negative values
                if(rx<=0 || ry<= 0 || rx>=5 || ry>=5){ continue;} //only draw small squares
                if((int)rx%2==(int)ry%2) {drawPixel(g2D,x+xo,y+yo,255,0,0);}
                else{ drawPixel(g2D,x+xo,y+yo,0,255,0);}
            }
        }
    }
    void clearBackground(Graphics2D g2D) {
        for(int y = 0; y < SCREEN_HEIGHT; y++) {
            for(int x = 0; x < SCREEN_WIDTH; x++) {
                drawPixel(g2D, x, y, 0, 60, 130);
            }
        }
    }

    //draw a pixel at x/y with rgb
    void drawPixel(Graphics2D g2D, int x, int y, int r, int g, int b)
    {
        g2D.setColor(new Color(r, g, b));
        g2D.fill(new Rectangle2D.Double(x*PIXEL_SCALE, SWING_SCREEN_HEIGHT-PIXEL_SCALE-(y*PIXEL_SCALE),
                PIXEL_SCALE, PIXEL_SCALE));
    }

    void movePlayer() {
        //turn left and right
        //if (!keys.m && keys.a) {player.a-=PLAYER_TURN_SPEED; if(player.a<0) {player.a+=360;}}
        //if (!keys.m && keys.d) {player.a+=PLAYER_TURN_SPEED; if(player.a>359) {player.a-=360;}}
        if(mouse.horizontal()==-1) {player.a-=PLAYER_TURN_SPEED; if(player.a<0) {player.a+=360;}}
        if(mouse.horizontal()==1) {player.a+=PLAYER_TURN_SPEED; if(player.a>359) {player.a-=360;}}
        //move forwards and backwards
        int deltaX = (int)(sin[player.a]*PLAYER_MOVE_SPEED);
        int deltaY = (int)(cos[player.a]*PLAYER_MOVE_SPEED);
        if (keys.w) {player.x+=deltaX;player.y+=deltaY;}
        if (keys.s) {player.x-=deltaX;player.y-=deltaY;}
        //strafe left and right
        if (keys.a) {player.x-=deltaY;player.y+=deltaX;}
        if (keys.d) {player.x+=deltaY;player.y-=deltaX;}
        //move up and down
        if (keys.space) {player.z+=4;}
        if (keys.shift) {player.z-=4;}
        //look up and down
        //if (keys.m && keys.a) {player.l-=1;}
        //if (keys.m && keys.d) {player.l+=1;}
        if(mouse.vertical()==1) {player.l-=1;}
        if(mouse.vertical()==-1) {player.l+=1;}
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
    int[] strToIntArray(String[] array) {
        int[] intArray = new int[array.length];
        for(int i = 0; i < array.length; i++){
            intArray[i] = Integer.parseInt(array[i]);
        }
        return intArray;
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_A) {keys.a = true;}
        if (e.getKeyCode() == KeyEvent.VK_D) {keys.d = true;}
        if (e.getKeyCode() == KeyEvent.VK_W) {keys.w = true;}
        if (e.getKeyCode() == KeyEvent.VK_S) {keys.s = true;}
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {keys.space = true;}
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {keys.shift = true;}
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {load();}
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            isCursorConfined = !isCursorConfined;
            if (isCursorConfined) {
                setCursor(invisibleCursor);
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_A) {keys.a = false;}
        if (e.getKeyCode() == KeyEvent.VK_D) {keys.d = false;}
        if (e.getKeyCode() == KeyEvent.VK_W) {keys.w = false;}
        if (e.getKeyCode() == KeyEvent.VK_S) {keys.s = false;}
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {keys.space = false;}
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {keys.shift = false;}
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
            System.out.println("right click");
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void run() {
        try{
            while (true){
                Thread.currentThread().sleep(1000/FRAMES_PER_SECOND);
                repaint();
            }
        }catch (Exception e){
            e.printStackTrace();;
        }
    }
    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Doom");
            Doom doom = new Doom();
            frame.add(doom);
            frame.pack();

            frame.setResizable(false);
            frame.setVisible(true);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.requestFocusInWindow();
        });

    }

    class Keys {
        boolean w, s, a, d, space, shift;
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

        @Override
        public String toString() {
            return "pos: ("+x+", "+y+", "+z+")\nplayer ang: ("+a+") look ang: ("+l+")\n";
        }
    }
    class Wall {
        //wall position
        int x1, y1, x2, y2;
        //color
        int c;
        int wt, u, v;
        int shade;
        public Wall() {}
        public Wall(int x1, int y1, int x2, int y2, int c) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.c = c;
        }
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
        //bottom and top color
        int c1, c2;
        //surface texture, surface scale
        int st, ss;
        //hold points for surfaces
        int[] surf = new int[SCREEN_WIDTH];
        //is there a surfaces to draw
        int surface;
        public Sector() {}
        public Sector(int ws, int we, int z1, int z2, int c1, int c2) {
            this.ws = ws; this.we = we;
            this.z1 = z1; this.z2 = z2;
            this.c1 = c1; this.c2 = c2;
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
    class MouseMovement {
        int xOld, yOld;
        int xNew, yNew;
        MouseMovement() {}
        int horizontal() {
            if(xNew - xOld==0){return 0;}
            else if(xNew - xOld > 0) {return 1;}
            else {return -1;}
        }
        int vertical() {
            if(yNew - yOld==0){return 0;}
            else if(yNew - yOld > 0) {return -1;}
            else {return 1;}
        }
    }
}