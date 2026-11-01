package sail.system.service;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.*;
import sail.system.dto.SystemRequests;
import sail.system.entity.*;
import sail.system.mapper.*;

import java.util.*;

/** 按若依式类型与数据分离管理，业务值稳定，展示资料可维护。 */
@Service
@RequiredArgsConstructor
public class DictionaryTypeService {
    private final SysDictionaryTypeMapper types;
    private final SysDictionaryMapper data;

    /** 类型查询在 SQL 过滤，避免前端只隐藏不匹配类型。 */
    public List<SysDictionaryType> list(ListQuery query) {
        return types.selectList(
                Wrappers.<SysDictionaryType>lambdaQuery()
                        .and(
                                StrUtil.isNotBlank(query.getKeyword()),
                                w ->
                                        w.like(SysDictionaryType::getName, query.getKeyword())
                                                .or()
                                                .like(
                                                        SysDictionaryType::getCode,
                                                        query.getKeyword()))
                        .eq(
                                query.enabledStatus() != null,
                                SysDictionaryType::getStatus,
                                query.enabledStatus())
                        .orderByAsc(SysDictionaryType::getId));
    }

    /** 修改类型时批量迁移其数据编码，不允许内置类型变更业务编码。 */
    @Transactional
    public long save(Long id, SystemRequests.DictionaryType input) {
        var entity = id == null ? new SysDictionaryType() : types.selectById(id);
        if (entity == null) throw new BusinessException("字典类型不存在");
        if (types.selectCount(
                        Wrappers.<SysDictionaryType>lambdaQuery()
                                .eq(SysDictionaryType::getCode, input.code())
                                .ne(id != null, SysDictionaryType::getId, id))
                > 0) throw new BusinessException("字典类型编码已存在");
        if (Boolean.TRUE.equals(entity.getBuiltIn())
                && !Objects.equals(entity.getCode(), input.code()))
            throw new BusinessException("系统字典类型编码不可修改");
        if (id != null && !Objects.equals(entity.getCode(), input.code()))
            data.update(
                    null,
                    Wrappers.<SysDictionary>lambdaUpdate()
                            .eq(SysDictionary::getTypeCode, entity.getCode())
                            .set(SysDictionary::getTypeCode, input.code()));
        entity.setCode(input.code());
        entity.setName(input.name());
        entity.setStatus(input.status());
        entity.setRemark(StrUtil.emptyIfNull(input.remark()));
        if (id == null) types.insert(entity);
        else types.updateById(entity);
        return entity.getId();
    }

    /** 有数据的类型必须先清空数据，系统类型不可删除。 */
    public void delete(long id) {
        var entity = types.selectById(id);
        if (entity == null) throw new BusinessException("字典类型不存在");
        if (Boolean.TRUE.equals(entity.getBuiltIn())) throw new BusinessException("系统字典类型不可删除");
        if (data.selectCount(
                        Wrappers.<SysDictionary>lambdaQuery()
                                .eq(SysDictionary::getTypeCode, entity.getCode()))
                > 0) throw new BusinessException("请先删除该类型下的字典数据");
        types.deleteById(id);
    }

    /** 登录用户只能获取已启用类型和数据，不暴露维护用的停用条目。 */
    public Map<String, List<SysDictionary>> options() {
        var enabled =
                types.selectList(
                        Wrappers.<SysDictionaryType>lambdaQuery()
                                .eq(SysDictionaryType::getStatus, 1));
        if (enabled.isEmpty()) return Map.of();
        Map<String, List<SysDictionary>> result = new LinkedHashMap<>();
        enabled.forEach(type -> result.put(type.getCode(), new ArrayList<>()));
        data.selectList(
                        Wrappers.<SysDictionary>lambdaQuery()
                                .in(SysDictionary::getTypeCode, result.keySet())
                                .eq(SysDictionary::getStatus, 1)
                                .orderByAsc(SysDictionary::getSortOrder, SysDictionary::getId))
                .forEach(item -> result.get(item.getTypeCode()).add(item));
        return result;
    }
}
