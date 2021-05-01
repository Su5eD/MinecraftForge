package cpw.mods.fml.common.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import net.minecraft.entity.*;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.util.MathHelper;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.EntityRegistry.EntityRegistration;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.common.registry.IThrowableEntity;

public class EntitySpawnPacket extends FMLPacket
{

    public int networkId;
    public int modEntityId;
    public int entityId;
    public double scaledX;
    public double scaledY;
    public double scaledZ;
    public float scaledYaw;
    public float scaledPitch;
    public float scaledHeadYaw;
    public List metadata;
    public int throwerId;
    public double speedScaledX;
    public double speedScaledY;
    public double speedScaledZ;
    public ByteArrayDataInput dataStream;
    public int rawX;
    public int rawY;
    public int rawZ;

    public EntitySpawnPacket()
    {
        super(Type.ENTITYSPAWN);
    }

    @Override
    public byte[] generatePacket(Object... data)
    {
        EntityRegistration er = (EntityRegistration) data[0];
        Entity ent = (Entity) data[1];
        NetworkModHandler handler = (NetworkModHandler) data[2];
        ByteArrayDataOutput dat = ByteStreams.newDataOutput();

        dat.writeInt(handler.getNetworkId());
        dat.writeInt(er.getModEntityId());
        // entity id
        dat.writeInt(ent.entityId);

        // entity pos x,y,z
        dat.writeInt(MathHelper.floor_double(ent.posX * 32D));
        dat.writeInt(MathHelper.floor_double(ent.posY * 32D));
        dat.writeInt(MathHelper.floor_double(ent.posZ * 32D));

        // yaw, pitch
        dat.writeByte((byte) (ent.rotationYaw * 256.0F / 360.0F));
        dat.writeByte((byte) (ent.rotationPitch * 256.0F / 360.0F));

        // head yaw
        if (ent instanceof EntityLiving)
        {
            dat.writeByte((byte) (((EntityLiving)ent).rotationYawHead * 256.0F / 360.0F));
        }
        else
        {
            dat.writeByte(0);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try
        {
            ent.getDataWatcher().writeWatchableObjects(dos);
        }
        catch (IOException e)
        {
            // unpossible
        }

        dat.write(bos.toByteArray());

        if (ent instanceof IThrowableEntity)
        {
            Entity owner = ((IThrowableEntity)ent).getThrower();
            dat.writeInt(owner == null ? ent.entityId : owner.entityId);
            double maxVel = 3.9D;
            double mX = ent.motionX;
            double mY = ent.motionY;
            double mZ = ent.motionZ;
            if (mX < -maxVel) mX = -maxVel;
            if (mY < -maxVel) mY = -maxVel;
            if (mZ < -maxVel) mZ = -maxVel;
            if (mX >  maxVel) mX =  maxVel;
            if (mY >  maxVel) mY =  maxVel;
            if (mZ >  maxVel) mZ =  maxVel;
            dat.writeInt((int)(mX * 8000D));
            dat.writeInt((int)(mY * 8000D));
            dat.writeInt((int)(mZ * 8000D));
        }
        else
        {
            dat.writeInt(0);
        }
        if (ent instanceof IEntityAdditionalSpawnData)
        {
            ((IEntityAdditionalSpawnData)ent).writeSpawnData(dat);
        }

        return dat.toByteArray();
    }

    @Override
    public FMLPacket consumePacket(byte[] data)
    {
        ByteArrayDataInput dat = ByteStreams.newDataInput(data);
        networkId = dat.readInt();
        modEntityId = dat.readInt();
        entityId = dat.readInt();
        rawX = dat.readInt();
        rawY = dat.readInt();
        rawZ = dat.readInt();
        scaledX = rawX / 32D;
        scaledY = rawY / 32D;
        scaledZ = rawZ / 32D;
        scaledYaw = dat.readByte() * 360F / 256F;
        scaledPitch = dat.readByte() * 360F / 256F;
        scaledHeadYaw = dat.readByte() * 360F / 256F;
        ByteArrayInputStream bis = new ByteArrayInputStream(data, 27, data.length - 27);
        DataInputStream dis = new DataInputStream(bis);
        try
        {
            metadata = DataWatcher.readWatchableObjects(dis);
        }
        catch (IOException e)
        {
            // Nope
        }
        dat.skipBytes(data.length - bis.available() - 27);
        throwerId = dat.readInt();
        if (throwerId != 0)
        {
            speedScaledX = dat.readInt() / 8000D;
            speedScaledY = dat.readInt() / 8000D;
            speedScaledZ = dat.readInt() / 8000D;
        }

        this.dataStream = dat;
        return this;
    }

    @Override
    public void execute(INetworkManager network, FMLNetworkHandler handler, NetHandler netHandler, String userName)
    {
        NetworkModHandler nmh = handler.findNetworkModHandler(networkId);
        ModContainer mc = nmh.getContainer();

        EntityRegistration registration = EntityRegistry.instance().lookupModSpawn(mc, modEntityId);
        Class<? extends Entity> cls =  registration.getEntityClass();
        if (cls == null)
        {
            FMLLog.log(Level.WARNING, "Missing mod entity information for %s : %d", mc.getModId(), modEntityId);
            return;
        }


        Entity entity = FMLCommonHandler.instance().spawnEntityIntoClientWorld(registration, this);
    }

}
